#!/bin/sh
# Backup completo semanal (RNF-007): começa uma nova cadeia, base dos incrementais diários.
set -eu
. "$(dirname "$0")/comum.sh"
travar

cadeia="$SIGH_BACKUP_DIR/cadeia-$(agora)-$$"
mkdir -p "$cadeia"

backup_fisico "$trabalho/dados"
# Só uma cópia íntegra vira base da cadeia e libera a retenção das antigas.
pg_verifybackup -q "$trabalho/dados"
cifrar_pasta "$trabalho/dados" "$cadeia/000-completo.tar.gz.gpg"
# O manifesto (nomes, tamanhos e somas dos arquivos, sem dados) fica para o próximo incremental.
cp "$trabalho/dados/backup_manifest" "$cadeia/000-completo.manifest"
echo "$cadeia" > "$SIGH_BACKUP_DIR/cadeia-atual"
log "backup completo gravado em $cadeia"
copiar_para_fora "$cadeia"

# Retenção: mantém as cadeias das últimas N semanas (padrão 5), sempre preservando a atual.
retencao=$(( ${SIGH_BACKUP_RETENCAO_SEMANAS:-5} * 7 ))
find "$SIGH_BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name 'cadeia-*' ! -path "$cadeia" -mtime +"$retencao" \
    -exec rm -rf {} +
