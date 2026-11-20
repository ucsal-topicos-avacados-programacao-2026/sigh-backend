#!/bin/sh
# Restaura uma cadeia (completo + incrementais) numa pasta de dados nova do PostgreSQL 17.
# Uso: restaurar.sh <pasta-da-cadeia> <pasta-de-destino-vazia>
# Decifra conferindo a integridade, combina (pg_combinebackup) e verifica tudo, inclusive o WAL
# (pg_verifybackup). A cadeia pode ser a local ou a cópia no destino externo.
set -eu
. "$(dirname "$0")/comum.sh"

cadeia="$1"; destino="$2"
[ ! -e "$destino" ] || [ -z "$(ls -A "$destino")" ] || { log "destino $destino não está vazio"; exit 1; }

partes=""
for arquivo in $(ls "$cadeia"/*.tar.gz.gpg | sort); do
    parte="$trabalho/$(basename "$arquivo" .tar.gz.gpg)"
    decifrar_para "$arquivo" "$parte"
    partes="$partes $parte"
done
# shellcheck disable=SC2086
pg_combinebackup -o "$destino" $partes
pg_verifybackup -q "$destino"
log "cadeia $(basename "$cadeia") restaurada em $destino"
