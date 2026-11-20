#!/bin/sh
# Restaura uma cadeia (completo + incrementais) numa pasta de dados nova do PostgreSQL 17.
# Uso: restaurar.sh <pasta-da-cadeia> <pasta-de-destino-vazia>
# Confere o HMAC de cada cópia, decifra, combina (pg_combinebackup) e verifica (pg_verifybackup).
set -eu
. "$(dirname "$0")/comum.sh"

cadeia="$1"; destino="$2"
[ ! -e "$destino" ] || [ -z "$(ls -A "$destino")" ] || { log "destino $destino não está vazio"; exit 1; }
trabalho=$(mktemp -d)
trap 'rm -rf "$trabalho"' EXIT

partes=""
for arquivo in $(ls "$cadeia"/*.tar.gz.enc | sort); do
    parte="$trabalho/$(basename "$arquivo" .tar.gz.enc)"
    decifrar_para "$arquivo" "$parte"
    partes="$partes $parte"
done
# shellcheck disable=SC2086
pg_combinebackup -o "$destino" $partes
pg_verifybackup -n "$destino"
log "cadeia $(basename "$cadeia") restaurada em $destino"
