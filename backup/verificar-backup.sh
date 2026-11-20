#!/bin/sh
# Alerta da rotina de backup (RNF-007). É o HEALTHCHECK da imagem: sai com erro, e o Docker marca o
# container como unhealthy (docker ps), quando:
# - no executor de backup (SIGH_BACKUP_DIR): a última execução agendada falhou ou foi pulada por outra
#   em andamento, ou não há backup concluído nas últimas SIGH_BACKUP_MAX_HORAS (padrão 26);
# - no servidor do banco (PostgreSQL rodando neste container): o arquivamento de WAL está falhando.
set -eu
problema=""
alertar() { problema="${problema:+$problema; }$1"; }

if [ -n "${SIGH_BACKUP_DIR:-}" ]; then
    estado="$SIGH_BACKUP_DIR/estado"
    horas="${SIGH_BACKUP_MAX_HORAS:-26}"
    if [ ! -f "$estado/ultimo-sucesso" ]; then
        alertar "nenhum backup concluído ainda"
    else
        sucesso=$(cut -d' ' -f1 "$estado/ultimo-sucesso")
        [ $(( $(date -u +%s) - sucesso )) -le $(( horas * 3600 )) ] || alertar "último backup concluído há mais de ${horas}h"
    fi
    if [ -f "$estado/ultima-falha" ] && [ "$(cut -d' ' -f1 "$estado/ultima-falha")" -ge "${sucesso:-0}" ]; then
        alertar "a última execução falhou ($(cut -d' ' -f2- "$estado/ultima-falha"))"
    fi
fi

if [ -f "${PGDATA:-/nao-existe}/postmaster.pid" ]; then
    senha="${POSTGRES_PASSWORD:-}"
    [ -n "$senha" ] || [ -z "${POSTGRES_PASSWORD_FILE:-}" ] || senha=$(cat "$POSTGRES_PASSWORD_FILE")
    falhando=$(PGPASSWORD="$senha" psql -U "${POSTGRES_USER:-postgres}" -d postgres -Atc \
        "select coalesce(last_failed_time > coalesce(last_archived_time, '-infinity'), false) from pg_stat_archiver" \
        2>/dev/null) || falhando="?"
    case "$falhando" in
        f) ;;
        t) alertar "o arquivamento de WAL está falhando" ;;
        *) alertar "o servidor do banco não respondeu" ;;
    esac
fi

if [ -n "$problema" ]; then
    echo "ALERTA sigh-backup: $problema"
    exit 1
fi
echo "sigh-backup: ok"
