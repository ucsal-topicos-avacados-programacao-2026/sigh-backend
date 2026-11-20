#!/bin/sh
# Sobe o PostgreSQL 17 do SIGH com a mesma imagem do backup (RNF-004, RNF-007):
# - só aceita conexão com TLS (pg_hba-tls.conf): sem TLS, a conexão é recusada;
# - certificado e chave do servidor em PEM, em SIGH_TLS_CERTIFICADO_BANCO e SIGH_TLS_CHAVE_BANCO
#   (caminhos de arquivos montados no container);
# - summarize_wal ligado (backup incremental) e, com SIGH_WAL_DIR, arquivamento de cada segmento de WAL
#   cifrado com SIGH_CHAVE_BACKUP (arquivar-wal.sh), no máximo a cada SIGH_WAL_INTERVALO segundos
#   (padrão 300).
# As demais variáveis são as da imagem oficial (POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB).
set -eu
: "${SIGH_TLS_CERTIFICADO_BANCO:?defina SIGH_TLS_CERTIFICADO_BANCO (certificado do servidor, PEM)}"
: "${SIGH_TLS_CHAVE_BANCO:?defina SIGH_TLS_CHAVE_BANCO (chave privada do servidor, PEM)}"

# O PostgreSQL só aceita a chave privada se ela for do usuário dele e legível só por ele.
install -d -o postgres -g postgres -m 700 /etc/sigh-banco
install -o postgres -g postgres -m 600 "$SIGH_TLS_CERTIFICADO_BANCO" /etc/sigh-banco/servidor.crt
install -o postgres -g postgres -m 600 "$SIGH_TLS_CHAVE_BANCO" /etc/sigh-banco/servidor.key
install -o postgres -g postgres -m 600 /opt/sigh-backup/pg_hba-tls.conf /etc/sigh-banco/pg_hba.conf

set -- postgres -c ssl=on -c ssl_min_protocol_version=TLSv1.2 \
    -c ssl_cert_file=/etc/sigh-banco/servidor.crt -c ssl_key_file=/etc/sigh-banco/servidor.key \
    -c hba_file=/etc/sigh-banco/pg_hba.conf -c password_encryption=scram-sha-256 \
    -c wal_level=replica -c summarize_wal=on "$@"
if [ -n "${SIGH_WAL_DIR:-}" ]; then
    : "${SIGH_CHAVE_BACKUP:?defina SIGH_CHAVE_BACKUP para cifrar os segmentos de WAL}"
    mkdir -p "$SIGH_WAL_DIR"
    chown postgres:postgres "$SIGH_WAL_DIR"
    chmod 700 "$SIGH_WAL_DIR"
    set -- "$@" -c archive_mode=on -c "archive_command=/opt/sigh-backup/arquivar-wal.sh %p %f" \
        -c "archive_timeout=${SIGH_WAL_INTERVALO:-300}"
fi
exec docker-entrypoint.sh "$@"
