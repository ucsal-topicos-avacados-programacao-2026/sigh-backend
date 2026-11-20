#!/bin/bash
# Sobe a API para o teste de carga (TST-04): HTTPS no próprio back-end, com certificado fictício,
# e o banco sigh_carga. Uso: carga/subir-api.sh <pasta-do-certificado>
# A pasta precisa ter api.pem e api-chave.pem (veja carga/README.md). Segredos vêm de SIGH_ENV.
set -euo pipefail
cd "$(dirname "$0")/.."
CERTIFICADOS=${1:?informe a pasta com api.pem e api-chave.pem}
shift
set -a; . "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"; set +a
exec java -jar target/sigh-project-0.0.1-SNAPSHOT.jar \
	--spring.datasource.url="jdbc:postgresql://localhost:5432/${SIGH_BANCO_CARGA:-sigh_carga}" \
	--server.port=8443 \
	--server.ssl.bundle=sigh-tls \
	--server.ssl.enabled-protocols=TLSv1.3,TLSv1.2 \
	--spring.ssl.bundle.pem.sigh-tls.keystore.certificate="file:$CERTIFICADOS/api.pem" \
	--spring.ssl.bundle.pem.sigh-tls.keystore.private-key="file:$CERTIFICADOS/api-chave.pem" \
	"$@"
