#!/bin/sh
# Backup completo semanal (RNF-007): começa uma nova cadeia, base dos incrementais diários.
# Com --descartar-anteriores, apaga as outras cadeias (aqui e no destino externo) e o WAL arquivado
# anterior a esta. Use depois da primeira subida de uma versão da criptografia ou de uma troca de
# chave: as cópias antigas guardam os valores antigos (em claro, ou com a chave que saiu).
set -eu
. "$(dirname "$0")/comum.sh"
registrar_estado "backup completo"
travar

descartar=""
[ "${1:-}" = "--descartar-anteriores" ] && descartar=sim

cadeia="$SIGH_BACKUP_DIR/cadeia-$(agora)-$$"
mkdir -p "$cadeia"

backup_fisico "$trabalho/dados"
# Só uma cópia íntegra vira base da cadeia e libera a retenção das antigas.
pg_verifybackup -q "$trabalho/dados"
cifrar_pasta "$trabalho/dados" "$cadeia/000-completo.tar.gz.gpg"
# O manifesto (nomes, tamanhos e somas dos arquivos, sem dados) fica para o próximo incremental, e o
# primeiro segmento de WAL da cópia, para a limpeza do WAL arquivado.
cp "$trabalho/dados/backup_manifest" "$cadeia/000-completo.manifest"
sed -n 's/^START WAL LOCATION: .*(file \([0-9A-F]*\)).*/\1/p' "$trabalho/dados/backup_label" > "$cadeia/000-completo.wal-inicio"
echo "$cadeia" > "$SIGH_BACKUP_DIR/cadeia-atual"
log "backup completo gravado em $cadeia"
copiar_para_fora "$cadeia"
copiar_wal_para_fora

if [ -n "$descartar" ]; then
    for pasta in "$SIGH_BACKUP_DIR" "${SIGH_BACKUP_DESTINO_EXTERNO:-}"; do
        [ -n "$pasta" ] && [ -d "$pasta" ] || continue
        find "$pasta" -mindepth 1 -maxdepth 1 -type d -name 'cadeia-*' ! -name "$(basename "$cadeia")" -exec rm -rf {} +
    done
    log "cadeias anteriores descartadas"
else
    # Retenção: mantém as cadeias das últimas N semanas (padrão 5), aqui e no destino externo, sempre
    # preservando a atual.
    retencao=$(( ${SIGH_BACKUP_RETENCAO_SEMANAS:-5} * 7 ))
    for pasta in "$SIGH_BACKUP_DIR" "${SIGH_BACKUP_DESTINO_EXTERNO:-}"; do
        [ -n "$pasta" ] && [ -d "$pasta" ] || continue
        find "$pasta" -mindepth 1 -maxdepth 1 -type d -name 'cadeia-*' ! -name "$(basename "$cadeia")" \
            -mtime +"$retencao" -exec rm -rf {} +
    done
fi

# O WAL arquivado só é útil a partir do completo mais antigo guardado: o anterior a ele sai.
limpar_wal
