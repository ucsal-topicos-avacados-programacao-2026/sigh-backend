#!/bin/sh
# Funções comuns da rotina de backup do SIGH (RNF-004, RNF-007).
# Requer PostgreSQL 17 (pg_basebackup, pg_combinebackup, pg_verifybackup), gpg, tar, gzip e flock.
# Conexão pelo padrão do libpq: PGHOST, PGPORT, PGUSER, PGPASSWORD (usuário com REPLICATION) e,
# em produção, PGSSLMODE=verify-full.
set -eu
set -o pipefail
umask 077

: "${SIGH_BACKUP_DIR:?defina SIGH_BACKUP_DIR (pasta das cópias)}"
: "${SIGH_CHAVE_BACKUP:?defina SIGH_CHAVE_BACKUP (chave das cópias, fora do repositório)}"
# Chave aleatória e longa (ex.: openssl rand -base64 32): a cifragem não pode depender de uma senha fraca.
[ "${#SIGH_CHAVE_BACKUP}" -ge 32 ] || { echo "SIGH_CHAVE_BACKUP precisa ter pelo menos 32 caracteres" >&2; exit 1; }

agora() { date -u +%Y%m%dT%H%M%SZ; }

log() { echo "[sigh-backup $(agora)] $*" >&2; }

trabalho=$(mktemp -d)
# A pasta de trabalho guarda o backup físico em claro e o arquivo da chave: some em qualquer saída.
trap 'rm -rf "$trabalho"' EXIT INT TERM HUP
# A chave vai para o gpg por arquivo, nunca pela linha de comando (que aparece no ps).
printf '%s' "$SIGH_CHAVE_BACKUP" > "$trabalho/.chave"
export GNUPGHOME="$trabalho/.gnupg"
mkdir -p "$GNUPGHOME" "$SIGH_BACKUP_DIR"

# Um backup por vez: completo e incremental nunca gravam na mesma cadeia ao mesmo tempo.
travar() {
    exec 9>"$SIGH_BACKUP_DIR/.trava"
    flock -n 9 || { log "outro backup está em andamento; nada foi feito"; exit 1; }
}

cadeia_atual() {
    [ -f "$SIGH_BACKUP_DIR/cadeia-atual" ] || { log "nenhum backup completo ainda; rode backup-completo.sh"; exit 1; }
    cat "$SIGH_BACKUP_DIR/cadeia-atual"
}

# Empacota a pasta e cifra com AES-256 (OpenPGP simétrico, com verificação de integridade e chave
# derivada com o máximo de iterações). Grava com nome temporário e renomeia só no fim.
cifrar_pasta() {
    pasta="$1"; destino="$2"
    tar -C "$pasta" -cf - . | gzip -6 \
        | gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$trabalho/.chave" \
            --symmetric --cipher-algo AES256 --s2k-digest-algo SHA512 --s2k-count 65011712 \
            --compress-algo none --output "$destino.parcial"
    mv "$destino.parcial" "$destino"
}

# Decifra e confere a integridade: cópia alterada ou chave errada interrompem a restauração.
decifrar_para() {
    arquivo="$1"; pasta="$2"
    mkdir -p "$pasta"
    if ! gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$trabalho/.chave" \
            --decrypt "$arquivo" > "$trabalho/parte.tar.gz" 2>"$trabalho/gpg.log"; then
        log "não foi possível decifrar $(basename "$arquivo"): cópia alterada ou chave errada"
        exit 1
    fi
    gzip -dc "$trabalho/parte.tar.gz" | tar -C "$pasta" -xf -
    rm -f "$trabalho/parte.tar.gz"
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

# Cópia fora do servidor (RNF-007: recuperar o prontuário mesmo se uma instituição falhar). O destino
# é uma pasta montada de outro local (outra instituição, NFS, armazenamento de objetos montado).
copiar_para_fora() {
    cadeia="$1"
    [ -n "${SIGH_BACKUP_DESTINO_EXTERNO:-}" ] || return 0
    externo="$SIGH_BACKUP_DESTINO_EXTERNO/$(basename "$cadeia")"
    mkdir -p "$externo"
    for arquivo in "$cadeia"/*; do
        [ -e "$externo/$(basename "$arquivo")" ] && continue
        cp "$arquivo" "$externo/$(basename "$arquivo").parcial"
        mv "$externo/$(basename "$arquivo").parcial" "$externo/$(basename "$arquivo")"
    done
    log "cadeia $(basename "$cadeia") copiada para o destino externo"
}
