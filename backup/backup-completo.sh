#!/bin/sh
# Backup completo semanal (RNF-007): começa uma nova cadeia, base dos incrementais diários.
set -eu
. "$(dirname "$0")/comum.sh"

cadeia="$SIGH_BACKUP_DIR/cadeia-$(agora)"
trabalho=$(mktemp -d)
trap 'rm -rf "$trabalho"' EXIT
mkdir -p "$cadeia"
umask 077

backup_fisico "$trabalho/dados"
cifrar_pasta "$trabalho/dados" "$cadeia/00-completo.tar.gz.enc"
# O manifesto (nomes, tamanhos e somas dos arquivos, sem dados) fica para o próximo incremental.
cp "$trabalho/dados/backup_manifest" "$cadeia/00-completo.manifest"
echo "$cadeia" > "$SIGH_BACKUP_DIR/cadeia-atual"
log "backup completo gravado em $cadeia"

# Retenção: mantém as cadeias das últimas N semanas (padrão 5).
retencao=$(( ${SIGH_BACKUP_RETENCAO_SEMANAS:-5} * 7 ))
find "$SIGH_BACKUP_DIR" -maxdepth 1 -type d -name 'cadeia-*' -mtime +"$retencao" -exec rm -rf {} +
