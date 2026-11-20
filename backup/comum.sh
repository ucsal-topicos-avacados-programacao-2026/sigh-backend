#!/bin/sh
# Funções comuns da rotina de backup do SIGH (RNF-004, RNF-007).
# Requer PostgreSQL 17 (pg_basebackup, pg_combinebackup), openssl, tar e gzip.
# Conexão pelo padrão do libpq: PGHOST, PGPORT, PGUSER, PGPASSWORD (usuário com REPLICATION).
set -eu

: "${SIGH_BACKUP_DIR:?defina SIGH_BACKUP_DIR (pasta das cópias)}"
: "${SIGH_CHAVE_BACKUP:?defina SIGH_CHAVE_BACKUP (chave das cópias, fora do repositório)}"

ITERACOES_PBKDF2=600000

agora() { date -u +%Y%m%dT%H%M%SZ; }

log() { echo "[sigh-backup $(agora)] $*" >&2; }

cadeia_atual() {
    [ -f "$SIGH_BACKUP_DIR/cadeia-atual" ] || { log "nenhum backup completo ainda; rode backup-completo.sh"; exit 1; }
    cat "$SIGH_BACKUP_DIR/cadeia-atual"
}

# Empacota uma pasta, cifra com AES-256 (chave derivada por PBKDF2) e grava um HMAC-SHA256 para
# detectar qualquer alteração na cópia antes de decifrar.
cifrar_pasta() {
    pasta="$1"; destino="$2"
    tar -C "$pasta" -cf - . | gzip -6 \
        | openssl enc -aes-256-cbc -pbkdf2 -iter "$ITERACOES_PBKDF2" -salt -pass env:SIGH_CHAVE_BACKUP -out "$destino"
    openssl dgst -sha256 -hmac "$SIGH_CHAVE_BACKUP" -r "$destino" | cut -d' ' -f1 > "$destino.hmac"
}

decifrar_para() {
    arquivo="$1"; pasta="$2"
    esperado=$(cat "$arquivo.hmac")
    obtido=$(openssl dgst -sha256 -hmac "$SIGH_CHAVE_BACKUP" -r "$arquivo" | cut -d' ' -f1)
    [ "$esperado" = "$obtido" ] || { log "HMAC não confere em $(basename "$arquivo"): cópia alterada ou chave errada"; exit 1; }
    mkdir -p "$pasta"
    openssl enc -d -aes-256-cbc -pbkdf2 -iter "$ITERACOES_PBKDF2" -pass env:SIGH_CHAVE_BACKUP -in "$arquivo" \
        | gzip -d | tar -C "$pasta" -xf -
}

# Backup físico em formato de pasta (exigido pelo pg_combinebackup). Com $2, é incremental em
# relação ao manifesto informado (PostgreSQL 17, exige summarize_wal = on no servidor).
backup_fisico() {
    pasta="$1"; manifesto_base="${2:-}"
    if [ -n "$manifesto_base" ]; then
        pg_basebackup -D "$pasta" -Fp -X stream -c fast --manifest-checksums=SHA256 --incremental="$manifesto_base"
    else
        pg_basebackup -D "$pasta" -Fp -X stream -c fast --manifest-checksums=SHA256
    fi
}
