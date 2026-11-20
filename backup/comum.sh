#!/bin/sh
# Funções comuns da rotina de backup do SIGH (RNF-004, RNF-007).
# Requer PostgreSQL 17 (pg_basebackup, pg_combinebackup, pg_verifybackup), gpg, tar, gzip e flock.
# Conexão pelo padrão do libpq: PGHOST, PGPORT, PGUSER, PGPASSWORD (usuário com REPLICATION) e
# PGSSLMODE=verify-full, com o certificado da autoridade em PGSSLROOTCERT.
set -eu
. "$(dirname "$0")/cripto.sh"

: "${SIGH_BACKUP_DIR:?defina SIGH_BACKUP_DIR (pasta das cópias)}"
mkdir -p "$SIGH_BACKUP_DIR"

# A conexão com o banco leva o backup inteiro: só com TLS e com o certificado do servidor conferido.
exigir_tls() {
    [ "${PGSSLMODE:-}" = "verify-full" ] || { log "defina PGSSLMODE=verify-full: o backup só conecta ao banco com TLS"; exit 1; }
}

# Estado da última execução agendada, lido pelo verificar-backup.sh (alerta de falha, RNF-007).
registrar_estado() {
    tipo="$1"
    ao_sair() {
        mkdir -p "$SIGH_BACKUP_DIR/estado"
        if [ "$1" -eq 0 ]; then
            echo "$(date -u +%s) $tipo" > "$SIGH_BACKUP_DIR/estado/ultimo-sucesso"
        else
            echo "$(date -u +%s) $tipo saiu com o código $1" > "$SIGH_BACKUP_DIR/estado/ultima-falha"
        fi
    }
}

# Um backup por vez: completo e incremental nunca gravam na mesma cadeia ao mesmo tempo. Um backup
# pulado por isso conta como falha (o alerta avisa).
travar() {
    exec 9>"$SIGH_BACKUP_DIR/.trava"
    flock -n 9 || { log "outro backup está em andamento; nada foi feito"; exit 1; }
}

cadeia_atual() {
    [ -f "$SIGH_BACKUP_DIR/cadeia-atual" ] || { log "nenhum backup completo ainda; rode backup-completo.sh"; exit 1; }
    cat "$SIGH_BACKUP_DIR/cadeia-atual"
}

# Empacota a pasta e cifra. Grava com nome temporário e renomeia só no fim.
cifrar_pasta() {
    pasta="$1"; destino="$2"
    # Um .parcial deixado por uma execução interrompida não pode travar as próximas.
    rm -f "$destino.parcial"
    tar -C "$pasta" -cf - . | gzip -6 | gpg_cifrar "$destino.parcial"
    mv "$destino.parcial" "$destino"
}

# Decifra e confere a integridade: cópia alterada ou chave errada interrompem a restauração.
decifrar_para() {
    arquivo="$1"; pasta="$2"
    mkdir -p "$pasta"
    if ! gpg_decifrar "$arquivo" > "$trabalho/parte.tar.gz" 2>"$trabalho/gpg.log"; then
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
    exigir_tls
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

# O WAL arquivado (SIGH_WAL_DIR, se o executor o enxerga) também vai para o destino externo.
copiar_wal_para_fora() {
    [ -n "${SIGH_BACKUP_DESTINO_EXTERNO:-}" ] && [ -n "${SIGH_WAL_DIR:-}" ] && [ -d "$SIGH_WAL_DIR" ] || return 0
    mkdir -p "$SIGH_BACKUP_DESTINO_EXTERNO/wal"
    for arquivo in "$SIGH_WAL_DIR"/*.gpg; do
        [ -e "$arquivo" ] || continue
        externo="$SIGH_BACKUP_DESTINO_EXTERNO/wal/$(basename "$arquivo")"
        [ -e "$externo" ] && continue
        cp "$arquivo" "$externo.parcial"
        mv "$externo.parcial" "$externo"
    done
}

# Apaga, na pasta de WAL $1, os segmentos anteriores a $2 (o primeiro segmento do backup completo mais
# antigo guardado junto daquela pasta). Os arquivos .history ficam.
limpar_wal_antes() {
    pasta="$1"; limite="$2"
    [ -n "$pasta" ] && [ -d "$pasta" ] || return 0
    for arquivo in "$pasta"/*.gpg; do
        [ -e "$arquivo" ] || continue
        nome=$(basename "$arquivo")
        segmento=$(printf '%s' "$nome" | cut -c1-24)
        case "$segmento" in *[!0-9A-F]*) continue ;; esac
        [ "${#segmento}" -eq 24 ] || continue
        if [ "$segmento" != "$limite" ] && [ "$(printf '%s\n%s\n' "$segmento" "$limite" | sort | head -n 1)" = "$segmento" ]; then
            rm -f "$arquivo"
        fi
    done
}

# Limpa o WAL de cada lugar (local e externo) pela cadeia mais antiga guardada naquele mesmo lugar.
limpar_wal() {
    for lugar in "$SIGH_BACKUP_DIR|${SIGH_WAL_DIR:-}" \
            "${SIGH_BACKUP_DESTINO_EXTERNO:-}|${SIGH_BACKUP_DESTINO_EXTERNO:+$SIGH_BACKUP_DESTINO_EXTERNO/wal}"; do
        cadeias="${lugar%%|*}"; wal="${lugar#*|}"
        [ -n "$cadeias" ] && [ -d "$cadeias" ] && [ -n "$wal" ] || continue
        mais_antiga=$(find "$cadeias" -mindepth 1 -maxdepth 1 -type d -name 'cadeia-*' | sort | head -n 1)
        # Sem o início registrado (cadeia anterior a esta versão), nada é apagado.
        if [ -n "$mais_antiga" ] && [ -s "$mais_antiga/000-completo.wal-inicio" ]; then
            limpar_wal_antes "$wal" "$(cat "$mais_antiga/000-completo.wal-inicio")"
        fi
    done
}
