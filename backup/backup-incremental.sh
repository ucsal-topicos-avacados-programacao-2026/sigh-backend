#!/bin/sh
# Backup incremental diário (RNF-007): só o que mudou desde o último backup da cadeia atual.
set -eu
. "$(dirname "$0")/comum.sh"
travar

cadeia=$(cadeia_atual)
ultimo=$(ls "$cadeia"/*.manifest | sort | tail -n 1)
sequencia=$(printf '%03d' "$(ls "$cadeia"/*.manifest | wc -l)")

backup_fisico "$trabalho/dados" "$ultimo"
cifrar_pasta "$trabalho/dados" "$cadeia/$sequencia-incremental.tar.gz.gpg"
cp "$trabalho/dados/backup_manifest" "$cadeia/$sequencia-incremental.manifest"
log "backup incremental $sequencia gravado em $cadeia"
copiar_para_fora "$cadeia"
