#!/bin/sh
# Restaura uma cadeia (completo + incrementais) numa pasta de dados nova do PostgreSQL 17.
# Uso: restaurar.sh <pasta-da-cadeia> <pasta-de-destino-vazia>
# Decifra conferindo a integridade, combina (pg_combinebackup) e verifica tudo, inclusive o WAL
# (pg_verifybackup). A cadeia pode ser a local ou a cópia no destino externo.
set -eu
. "$(dirname "$0")/comum.sh"

cadeia="$1"; destino="$2"
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
