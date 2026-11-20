#!/bin/sh
# Elimina do servidor do banco os segmentos de WAL que podem guardar dado clínico em claro de antes da
# cifragem (BE-26, RNF-004). Rode no container do banco depois da migração da cifragem e de cada troca de
# chave, antes do backup-completo.sh --descartar-anteriores (veja o README):
#   docker exec <container do banco> limpar-wal-legado.sh
#
# Por que é preciso: o INSERT do dado legado e o UPDATE da migração ficam no WAL. Além disso, o PostgreSQL
# reaproveita segmentos antigos renomeando-os para uso futuro (wal_recycle), e só sobrescreve o que vai
# gravando: o resto do arquivo continua com o conteúdo antigo. O script:
#  1. desliga o reaproveitamento (wal_recycle=off; a imagem já sobe assim): segmento antigo passa a ser
#     apagado, e o novo nasce zerado;
#  2. grava WAL de enchimento até passar do segmento mais adiantado que existia: os segmentos do atual em
#     diante (inclusive os reaproveitados para uso futuro) são sobrescritos por inteiro;
#  3. espera o arquivamento e faz checkpoints até nenhum segmento de antes existir mais. Os anteriores ao
#     atual, onde estão o INSERT legado e a migração, são apagados, não sobrescritos: o conteúdo pode ficar
#     nos blocos livres do disco até ser reaproveitado (veja os limites no README).
# Sai com erro, dizendo o motivo, se algum segmento de antes continuar no servidor.
#
# Antes de rodar: pause o agendador de backup (o pg_basebackup cria um slot que retém WAL) e confira o
# espaço livre: o enchimento grava o mesmo volume no WAL e nos dados (o script confere e avisa).
#
# Conecta ao servidor local como superusuário, com as variáveis da imagem oficial (POSTGRES_USER,
# POSTGRES_PASSWORD, POSTGRES_DB). SIGH_LIMPEZA_ESPERA: segundos de espera pelo arquivamento (padrão 600).
set -eu
set -o pipefail
umask 077

agora() { date -u +%Y%m%dT%H%M%SZ; }
log() { echo "[sigh-wal $(agora)] $*" >&2; }

# A senha vai pelo ambiente do psql, nunca pela linha de comando. Sem PGOPTIONS: uma opção da sessão
# mudaria o que o script lê, sem mudar o que o servidor faz.
export PGPASSWORD="${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}"
unset PGOPTIONS
sql() {
    psql -X -q -At -v ON_ERROR_STOP=1 -h /var/run/postgresql -U "${POSTGRES_USER:-postgres}" \
        -d "${POSTGRES_DB:-postgres}" -c "$1"
}
espera=${SIGH_LIMPEZA_ESPERA:-600}
trabalho=$(mktemp -d)
trap 'rm -rf "$trabalho"' EXIT

[ "$(sql "select rolsuper from pg_roles where rolname = current_user")" = t ] \
    || { log "o usuário precisa ser superusuário (checkpoint, pg_ls_waldir, alter system)"; exit 1; }
# Com wal_level=minimal, uma tabela criada na mesma transação não vai para o WAL: o enchimento não encheria nada.
[ "$(sql "show wal_level")" != minimal ] || { log "wal_level=minimal não é suportado"; exit 1; }

# Quem apaga ou reaproveita segmentos é o checkpointer, que só vê a configuração do servidor (linha de comando
# ou arquivo). Um valor definido para o usuário ou o banco mudaria só o que esta sessão vê.
desligado() {
    [ "$(sql "select setting = 'off' and source in ('command line', 'configuration file')
              from pg_settings where name = 'wal_recycle'")" = t ]
}
if ! desligado; then
    sql "alter system set wal_recycle = off" >/dev/null
    sql "select pg_reload_conf()" >/dev/null
    for _ in $(seq 1 30); do
        desligado && break
        sleep 1
    done
    desligado || { log "wal_recycle continua ligado no servidor ($(sql "select setting || ', definido em ' || source from pg_settings where name = 'wal_recycle'")); confira a linha de comando (-c wal_recycle=on) e ALTER ROLE/DATABASE"; exit 1; }
fi

# Os segmentos que existem agora: podem ter o dado de antes, inclusive os reaproveitados para uso futuro.
# Inclui os .partial (fim de uma linha do tempo antiga), que também podem ter o dado de antes.
segmentos() { sql "select name from pg_ls_waldir() where name ~ '^[0-9A-F]{24}(\\.partial)?$' order by name"; }
segmentos > "$trabalho/antes"
quantos=$(wc -l < "$trabalho/antes" | tr -d ' ')
tamanho=$(sql "select pg_size_bytes(current_setting('wal_segment_size'))")
# Posição de um segmento na linha do tempo: o nome é linha do tempo (8), log (8) e segmento (8), em hexadecimal.
por_log=$(( 4294967296 / tamanho ))
posicao() { echo $(( 0x$(echo "$1" | cut -c9-16) * por_log + 0x$(echo "$1" | cut -c17-24) )); }
atual=$(sql "select pg_walfile_name(pg_current_wal_lsn())")
# O enchimento vai do segmento atual até passar do mais adiantado que existe, com dois de folga: cada um
# deles é usado e preenchido por inteiro. O mais adiantado é o de maior posição, de qualquer linha do tempo.
mais_adiantado=0
while read -r nome; do
    p=$(posicao "$nome")
    [ "$p" -gt "$mais_adiantado" ] && mais_adiantado=$p
done < "$trabalho/antes"
adiante=$(( mais_adiantado - $(posicao "$atual") ))
[ "$adiante" -ge 0 ] || adiante=0
megas=$(( (adiante + 2) * tamanho / 1048576 ))
# O enchimento ocupa o mesmo volume no WAL e nos dados, na mesma transação: sem espaço no WAL, o servidor
# cai. Exige o dobro, com 1 GB de folga.
# -P: uma linha por sistema de arquivos, mesmo com o nome do dispositivo longo (que o df quebraria em duas).
livre=$(( $(df -Pk "${PGDATA:-/var/lib/postgresql/data}" | tail -1 | awk '{print $4}') / 1024 ))
if [ "$livre" -lt $(( 2 * megas + 1024 )) ]; then
    log "espaço insuficiente: o enchimento precisa de $(( 2 * megas + 1024 )) MB livres e há $livre MB"
    exit 1
fi
log "$quantos segmento(s) de WAL no servidor, até $adiante adiante do atual; gravando $megas MB de enchimento"
# Armazenamento EXTERNAL: sem compressão, cada linha grava 1 MB de verdade no WAL.
sql "create table sigh_limpeza_wal (dado text);
     alter table sigh_limpeza_wal alter column dado set storage external;
     insert into sigh_limpeza_wal select repeat('0', 1048576) from generate_series(1, $megas);
     drop table sigh_limpeza_wal" >/dev/null

arquivamento=$(sql "show archive_mode")
for tentativa in 1 2 3 4 5; do
    # Uma escrita antes da troca: sem nada novo no segmento, pg_switch_wal não troca.
    sql "create table sigh_limpeza_wal_marca (); drop table sigh_limpeza_wal_marca" >/dev/null
    segmento=$(sql "select pg_walfile_name(pg_switch_wal())")
    if [ "$arquivamento" != off ]; then
        # O servidor só apaga um segmento depois de arquivá-lo.
        decorrido=0
        until [ "$(sql "select coalesce(last_archived_wal, '') >= '$segmento' from pg_stat_archiver")" = t ]; do
            if [ "$decorrido" -ge "$espera" ]; then
                log "o arquivamento do WAL está parado ($(sql "select failed_count || ' falha(s), última: ' || coalesce(last_failed_wal, '-') from pg_stat_archiver")): os segmentos de antes não podem ser apagados"
                exit 1
            fi
            sleep 1
            decorrido=$((decorrido + 1))
        done
    fi
    sql "checkpoint" >/dev/null
    segmentos > "$trabalho/agora"
    restantes=$(grep -c -F -x -f "$trabalho/antes" "$trabalho/agora" || true)
    [ "$restantes" -eq 0 ] && break
    log "tentativa $tentativa: $restantes segmento(s) de antes ainda no servidor"
    # O resumo do WAL (summarize_wal, para o backup incremental) também retém segmentos até alcançá-los.
    sleep 3
done

if [ "$restantes" -ne 0 ]; then
    log "$restantes segmento(s) de antes continuam no servidor. Retêm WAL: wal_keep_size=$(sql "show wal_keep_size"), slots de replicação=$(sql "select count(*) from pg_replication_slots") (um backup em andamento cria um), resumo do WAL até $(sql "select coalesce(summarized_lsn::text, '-') from pg_get_wal_summarizer_state()")"
    exit 1
fi
log "limpeza concluída: os $quantos segmento(s) de antes saíram do servidor (os do atual em diante, sobrescritos; os anteriores, apagados); o WAL atual nasceu depois da limpeza"
