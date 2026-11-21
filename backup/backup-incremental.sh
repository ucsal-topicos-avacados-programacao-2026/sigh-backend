#!/bin/sh
# Backup incremental diário (RNF-007): só o que mudou desde o último backup da cadeia atual.
set -eu
# Sem nenhum completo ainda (o inicial do agendador falhou, por exemplo, com o banco ainda subindo), o
# agendado de hoje vira o completo: sem isso, todo incremental falharia até o domingo.
if [ -n "${SIGH_BACKUP_DIR:-}" ] && [ ! -f "$SIGH_BACKUP_DIR/cadeia-atual" ]; then
    exec "$(dirname "$0")/backup-completo.sh"
fi
. "$(dirname "$0")/comum.sh"
registrar_estado "backup incremental"
travar

cadeia=$(cadeia_atual)
ultimo=$(ls "$cadeia"/*.manifest | sort | tail -n 1)
sequencia=$(printf '%03d' "$(ls "$cadeia"/*.manifest | wc -l)")

backup_fisico "$trabalho/dados" "$ultimo"
cifrar_pasta "$trabalho/dados" "$cadeia/$sequencia-incremental.tar.gz.gpg"
cp "$trabalho/dados/backup_manifest" "$cadeia/$sequencia-incremental.manifest"
log "backup incremental $sequencia gravado em $cadeia"
copiar_para_fora "$cadeia"
copiar_wal_para_fora
