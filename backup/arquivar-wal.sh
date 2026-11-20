#!/bin/sh
# archive_command do PostgreSQL (RNF-004, RNF-007): comprime e cifra cada segmento de WAL em
# SIGH_WAL_DIR. Com os segmentos, a restauração recupera até o último arquivado, e não só até o último
# backup (perda máxima de archive_timeout, em vez de um dia).
# Uso, pelo servidor: arquivar-wal.sh %p %f
set -eu
. "$(dirname "$0")/cripto.sh"
: "${SIGH_WAL_DIR:?defina SIGH_WAL_DIR (pasta dos segmentos de WAL arquivados)}"

origem="$1"; nome="$2"; destino="$SIGH_WAL_DIR/$nome.gpg"
if [ -e "$destino" ]; then
    # O servidor repete um segmento quando caiu antes de registrar o arquivamento. Só aceita se for o
    # mesmo conteúdo; nunca sobrescreve um segmento diferente.
    if gpg_decifrar "$destino" 2>/dev/null | gzip -dc | cmp -s - "$origem"; then
        exit 0
    fi
    log "segmento $nome já arquivado com outro conteúdo; nada foi sobrescrito"
    exit 1
fi
# Um .parcial deixado por uma queda no meio não pode travar o arquivamento (e encher o disco do banco).
rm -f "$destino.parcial"
gzip -c "$origem" | gpg_cifrar "$destino.parcial"
# Só confirma ao servidor (que então apaga o original) depois de o segmento estar gravado em disco.
sync "$destino.parcial"
mv "$destino.parcial" "$destino"
sync "$SIGH_WAL_DIR"
