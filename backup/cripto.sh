#!/bin/sh
# Cifragem das cópias do SIGH (RNF-004): chave, pasta de trabalho e gpg. Usado pela rotina de backup
# e pelo arquivamento de WAL. A chave vai para o gpg por arquivo, nunca pela linha de comando (que
# aparece no ps).
set -eu
set -o pipefail
umask 077

: "${SIGH_CHAVE_BACKUP:?defina SIGH_CHAVE_BACKUP (chave das cópias, fora do repositório)}"
# Chave aleatória e longa (ex.: openssl rand -base64 32): a cifragem não pode depender de uma senha fraca.
[ "${#SIGH_CHAVE_BACKUP}" -ge 32 ] || { echo "SIGH_CHAVE_BACKUP precisa ter pelo menos 32 caracteres" >&2; exit 1; }

agora() { date -u +%Y%m%dT%H%M%SZ; }

log() { echo "[sigh-backup $(agora)] $*" >&2; }

trabalho=$(mktemp -d)
# A pasta de trabalho guarda dados em claro e o arquivo da chave: some em qualquer saída, e o
# gpg-agent (que guarda a chave na memória) é encerrado junto. ao_sair é redefinida por quem precisa
# fazer algo a mais na saída (registrar o estado do backup).
ao_sair() { :; }
limpar() { codigo=${1:-$?}; ao_sair "$codigo" || true; gpgconf --kill gpg-agent 2>/dev/null || true; rm -rf "$trabalho"; }
trap limpar EXIT
trap 'trap - EXIT; limpar 130; exit 130' INT
trap 'trap - EXIT; limpar 143; exit 143' TERM
trap 'trap - EXIT; limpar 129; exit 129' HUP
printf '%s' "$SIGH_CHAVE_BACKUP" > "$trabalho/.chave"
export GNUPGHOME="$trabalho/.gnupg"
mkdir -p "$GNUPGHOME"

# Cifra a entrada padrão em $1 com AES-256 (OpenPGP simétrico, com verificação de integridade e chave
# derivada com o máximo de iterações).
gpg_cifrar() {
    gpg --batch --quiet --no-symkey-cache --pinentry-mode loopback --passphrase-file "$trabalho/.chave" \
        --symmetric --cipher-algo AES256 --s2k-digest-algo SHA512 --s2k-count 65011712 \
        --compress-algo none --output "$1"
}

# Decifra $1 para a saída padrão conferindo a integridade: cópia alterada ou chave errada falham.
gpg_decifrar() {
    gpg --batch --quiet --no-symkey-cache --pinentry-mode loopback --passphrase-file "$trabalho/.chave" \
        --decrypt "$1"
}
