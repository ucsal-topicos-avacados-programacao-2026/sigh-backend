#!/bin/bash
# Ensaio de restauração do TST-04 (RNF-004, RNF-007), com o volume dos dados de carga.
#
# Usa a mesma imagem e os mesmos scripts de produção (docker/backup e backup/):
#  1. sobe um banco com TLS e arquivamento de WAL cifrado, e carrega nele os dados do banco de carga;
#  2. faz um backup completo, altera dados, faz um incremental e altera de novo (essa última mudança só
#     volta pelo WAL arquivado);
#  3. restaura a partir da cópia externa, sobe a cópia e compara todas as tabelas com a origem;
#  4. procura o texto clínico em claro nas cópias, no WAL arquivado e nos arquivos restaurados.
# Mede o tempo e o tamanho de cada etapa. Os containers, a rede e o volume são apagados no fim.
#
# Uso: carga/ensaiar-restauracao.sh   (com o banco de carga pronto no container sigh-db)
# Variáveis: SIGH_ENV (padrão ~/.config/sigh/sigh-local.env, com SIGH_CHAVE_BACKUP),
# SIGH_BANCO_CARGA (padrão sigh_carga), SIGH_CONTAINER_ORIGEM (padrão sigh-db).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"; set +a
ORIGEM_BANCO=${SIGH_BANCO_CARGA:-sigh_carga}
ORIGEM_CONTAINER=${SIGH_CONTAINER_ORIGEM:-sigh-db}
ID="sigh-ensaio-$$"
IMAGEM="sigh-backup:ensaio"
SENHA_BANCO=$(openssl rand -hex 16)
TRABALHO=$(mktemp -d)
CERTIFICADOS="$TRABALHO/banco"
AUTORIDADE="$TRABALHO/executor"
mkdir "$CERTIFICADOS" "$AUTORIDADE"
# Texto que existe em todo registro clínico dos dados de carga, só em ASCII: grep em bytes (LC_ALL=C).
CLINICO="esquerdo, sudorese e n"
# Controle positivo: nome de paciente, que não é dado clínico e fica em claro no banco.
NAO_CLINICO="Paciente 1999 (carga)"

limpar() {
	docker rm -f "$ID-banco" "$ID-executor" >/dev/null 2>&1 || true
	docker network rm "$ID" >/dev/null 2>&1 || true
	docker volume rm "$ID-wal" >/dev/null 2>&1 || true
	rm -rf "$TRABALHO"
}
trap limpar EXIT

agora() { python3 -c 'import time; print(time.time())'; }
duracao() { python3 -c "print(f'{$2 - $1:.1f} s')"; }
no_executor() { docker exec "$ID-executor" sh -c "$1"; }
sql() { no_executor "psql -d sigh -Atc \"$1\""; }

echo "== Preparação"
docker build -q -t "$IMAGEM" -f docker/backup/Dockerfile . >/dev/null
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 2 -subj "/CN=banco" \
	-addext "subjectAltName=DNS:banco" -keyout "$CERTIFICADOS/servidor.key" -out "$CERTIFICADOS/servidor.crt" 2>/dev/null
docker network create "$ID" >/dev/null
docker volume create "$ID-wal" >/dev/null

docker create --name "$ID-banco" --network "$ID" --network-alias banco \
	-e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$SENHA_BANCO" -e POSTGRES_DB=sigh \
	-e SIGH_TLS_CERTIFICADO_BANCO=/certs/servidor.crt -e SIGH_TLS_CHAVE_BANCO=/certs/servidor.key \
	-e SIGH_WAL_DIR=/wal -e SIGH_CHAVE_BACKUP -v "$ID-wal:/wal" "$IMAGEM" iniciar-banco.sh >/dev/null
docker cp -q "$CERTIFICADOS/." "$ID-banco:/certs/"
docker start "$ID-banco" >/dev/null

docker create --name "$ID-executor" --network "$ID" \
	-e PGHOST=banco -e PGPORT=5432 -e PGUSER=postgres -e PGPASSWORD="$SENHA_BANCO" \
	-e PGSSLMODE=verify-full -e PGSSLROOTCERT=/certs/servidor.crt \
	-e SIGH_BACKUP_DIR=/backups -e SIGH_BACKUP_DESTINO_EXTERNO=/externo -e SIGH_CHAVE_BACKUP -e SIGH_WAL_DIR=/wal \
	-v "$ID-wal:/wal" "$IMAGEM" sleep infinity >/dev/null
# O executor só recebe o certificado do banco, para conferi-lo; a chave privada fica no banco.
cp "$CERTIFICADOS/servidor.crt" "$AUTORIDADE/"
docker cp -q "$AUTORIDADE/." "$ID-executor:/certs/"
docker start "$ID-executor" >/dev/null

for _ in $(seq 1 120); do
	no_executor "psql -d sigh -Atc 'select 1'" >/dev/null 2>&1 && break
	sleep 1
done
echo "conexão do executor com o banco: $(sql "select 'TLS ' || version || ', cifra ' || cipher from pg_stat_ssl where pid = pg_backend_pid()")"

inicio=$(agora)
docker exec "$ORIGEM_CONTAINER" pg_dump -U postgres -Fc --no-owner "$ORIGEM_BANCO" \
	| docker exec -i "$ID-executor" pg_restore -d sigh --no-owner --exit-on-error
sql "analyze" >/dev/null
echo "dados de carga carregados em $(duracao "$inicio" "$(agora)"): $(sql "select count(*) from registro_clinico") registros clínicos, banco com $(sql "select pg_size_pretty(pg_database_size('sigh'))")"

echo "== Backup"
inicio=$(agora)
no_executor "backup-completo.sh --descartar-anteriores" >/dev/null
echo "completo: $(duracao "$inicio" "$(agora)"), $(no_executor 'du -sh /backups/$(basename $(cat /backups/cadeia-atual))/000-completo.tar.gz.gpg' | cut -f1)"

sql "update paciente set nome = nome || ' (alterado 1)' where cpf like '1%' and right(nome, 7) = '(carga)'" >/dev/null
sql "update atendimento set status = 'ENCERRADO' where status = 'EM_ATENDIMENTO' and motivo like '%há 1 dias'" >/dev/null
inicio=$(agora)
no_executor "backup-incremental.sh" >/dev/null
echo "incremental: $(duracao "$inicio" "$(agora)"), $(no_executor 'du -sh /backups/$(basename $(cat /backups/cadeia-atual))/001-incremental.tar.gz.gpg' | cut -f1)"

# Depois do último backup: só volta pela restauração do WAL arquivado.
sql "insert into instituicao (id, nome, tipo, credenciada) values (gen_random_uuid(), 'Hospital C (depois do backup)', 'PUBLICA', true)" >/dev/null
segmento=$(sql "select pg_walfile_name(pg_switch_wal())")
for _ in $(seq 1 120); do
	no_executor "test -f /wal/$segmento.gpg" && break
	sleep 0.5
done
no_executor "test -f /wal/$segmento.gpg"
echo "WAL arquivado até o segmento $segmento"

impressao() {
	local destino=$1 consulta="" tabela
	for tabela in $(sql "select tablename from pg_tables where schemaname = 'public' order by 1"); do
		consulta+="${consulta:+ union all }select '$tabela', count(*), md5(coalesce(string_agg(t::text, '|' order by t::text), '')) from $tabela t"
	done
	no_executor "$destino psql -d sigh -At -F ' ' -c \"$consulta\""
}
impressao "" > "$TRABALHO/origem.txt"

echo "== Restauração"
cadeia=$(no_executor 'basename $(cat /backups/cadeia-atual)')
inicio=$(agora)
no_executor "restaurar.sh /externo/$cadeia /restaurado /wal" >/dev/null
no_executor "chown -R postgres:postgres /restaurado && chmod 700 /restaurado && su postgres -s /bin/sh -c \"pg_ctl -D /restaurado -l /tmp/restaurado.log -o '-p 5433 -c listen_addresses=127.0.0.1' start\" </dev/null >/dev/null 2>&1"
for _ in $(seq 1 240); do
	[ "$(no_executor "PGSSLMODE=disable psql -h 127.0.0.1 -p 5433 -d sigh -Atc 'select not pg_is_in_recovery()'" 2>/dev/null)" = "t" ] && break
	sleep 0.5
done
echo "restaurado e no ar em $(duracao "$inicio" "$(agora)") (decifrar, combinar, verificar e aplicar o WAL)"
impressao "PGSSLMODE=disable PGHOST=127.0.0.1 PGPORT=5433" > "$TRABALHO/restaurado.txt"

echo "== Comparação com a origem (linhas e md5 de cada tabela)"
if diff "$TRABALHO/origem.txt" "$TRABALHO/restaurado.txt" >/dev/null; then
	echo "IGUAL em $(wc -l < "$TRABALHO/origem.txt" | tr -d ' ') tabelas"
else
	echo "DIFERENTE:"; diff "$TRABALHO/origem.txt" "$TRABALHO/restaurado.txt" || true
	exit 1
fi
awk '$1 ~ /^(paciente|registro_clinico|atendimento|instituicao|usuario)$/ {print "  " $1 ": " $2 " linhas"}' "$TRABALHO/origem.txt"

echo "== Texto clínico em claro"
no_executor "PGSSLMODE=disable psql -h 127.0.0.1 -p 5433 -d sigh -Atc 'checkpoint'" >/dev/null
procurar() { no_executor "LC_ALL=C grep -r -a -l -F '$1' $2 | wc -l" | tr -d ' '; }
echo "controle (nome de paciente, não clínico) nos arquivos restaurados: $(procurar "$NAO_CLINICO" /restaurado/base) arquivo(s)"
echo "texto clínico nos arquivos restaurados: $(procurar "$CLINICO" /restaurado) arquivo(s)"
echo "texto clínico nas cópias (cifradas): $(procurar "$CLINICO" /backups) arquivo(s)"
echo "texto clínico no WAL arquivado (cifrado): $(procurar "$CLINICO" /wal) arquivo(s)"
