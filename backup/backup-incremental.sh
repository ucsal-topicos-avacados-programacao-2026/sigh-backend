#!/bin/sh
# Backup incremental diário (RNF-007): só o que mudou desde o último backup da cadeia atual.
set -eu
. "$(dirname "$0")/comum.sh"

cadeia=$(cadeia_atual)
ultimo=$(ls "$cadeia"/*.manifest | sort | tail -n 1)
sequencia=$(printf '%02d' $(( $(ls "$cadeia"/*.manifest | wc -l) )))
trabalho=$(mktemp -d)
trap 'rm -rf "$trabalho"' EXIT
umask 077

backup_fisico "$trabalho/dados" "$ultimo"
cifrar_pasta "$trabalho/dados" "$cadeia/$sequencia-incremental.tar.gz.enc"
cp "$trabalho/dados/backup_manifest" "$cadeia/$sequencia-incremental.manifest"
log "backup incremental $sequencia gravado em $cadeia"
