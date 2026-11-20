#!/bin/bash
# Carrega os dados fictícios de carga (dados-de-carga.sql) no banco de carga, com a API parada.
# A senha e o segredo do segundo fator vão pela entrada padrão do psql (\set), nunca como argumento
# de comando: assim não aparecem na lista de processos, nem no host nem no container.
# Variáveis: SIGH_ENV (com SIGH_SENHA_CARGA e SIGH_MFA_CARGA), SIGH_BANCO_CARGA, SIGH_CONTAINER_ORIGEM.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"; set +a
: "${SIGH_SENHA_CARGA:?defina SIGH_SENHA_CARGA}" "${SIGH_MFA_CARGA:?defina SIGH_MFA_CARGA}"
{
	printf '\\set senha %s\n\\set segredo_mfa %s\n' "$SIGH_SENHA_CARGA" "$SIGH_MFA_CARGA"
	cat carga/dados-de-carga.sql
} | docker exec -i "${SIGH_CONTAINER_ORIGEM:-sigh-db}" psql -q -U postgres -d "${SIGH_BANCO_CARGA:-sigh_carga}"
