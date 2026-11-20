#!/bin/sh
# Restaura uma cadeia (completo + incrementais) numa pasta de dados nova do PostgreSQL 17.
# Uso: restaurar.sh <pasta-da-cadeia> <pasta-de-destino-vazia> [pasta-dos-segmentos-de-wal]
# Decifra conferindo a integridade, combina (pg_combinebackup) e verifica tudo, inclusive o WAL
# (pg_verifybackup). A cadeia pode ser a local ou a cópia no destino externo. Com a pasta dos
# segmentos de WAL arquivados, o servidor, ao subir, aplica também o que mudou depois do último
# backup, até o último segmento arquivado; precisa de SIGH_CHAVE_BACKUP no ambiente do servidor.
set -eu
. "$(dirname "$0")/comum.sh"

cadeia="$1"; destino="$2"; wal="${3:-}"
# O caminho do WAL vai para o postgresql.auto.conf entre aspas: aspas nele corromperiam o arquivo.
case "$wal" in *\'*|*\"*) log "o caminho do WAL não pode ter aspas"; exit 1 ;; esac
[ ! -e "$destino" ] || [ -z "$(ls -A "$destino")" ] || { log "destino $destino não está vazio"; exit 1; }

# O glob já vem em ordem (000-completo, 001-incremental, ...); aspas protegem caminhos com espaço.
set --
for arquivo in "$cadeia"/*.tar.gz.gpg; do
    [ -e "$arquivo" ] || { log "nenhuma cópia em $cadeia"; exit 1; }
    parte="$trabalho/$(basename "$arquivo" .tar.gz.gpg)"
    decifrar_para "$arquivo" "$parte"
    set -- "$@" "$parte"
done
pg_combinebackup -o "$destino" "$@"
pg_verifybackup -q "$destino"
log "cadeia $(basename "$cadeia") restaurada em $destino"
if [ -n "$wal" ]; then
    [ -d "$wal" ] || { log "pasta de WAL $wal não existe"; exit 1; }
    scripts=$(cd "$(dirname "$0")" && pwd)
    printf "restore_command = '%s/recuperar-wal.sh \"%s\" %%f %%p'\n" "$scripts" "$wal" >> "$destino/postgresql.auto.conf"
    # A cópia restaurada não arquiva no WAL de produção: a nova linha do tempo dela desviaria as próximas
    # restaurações. Para pô-la em uso com iniciar-banco.sh, use outro SIGH_WAL_DIR.
    echo "archive_mode = off" >> "$destino/postgresql.auto.conf"
    touch "$destino/recovery.signal"
    log "ao subir, o servidor aplica os segmentos de WAL de $wal até o último arquivado"
fi
