#!/bin/sh
# Ponto de entrada da imagem de backup: agenda o completo (domingo) e o incremental (segunda a
# sábado) no crond e o mantém em primeiro plano. O crond não herda o ambiente do container, então
# as variáveis da rotina vão para um arquivo legível só pelo root. A saída de cada execução vai para
# o log do container, e o estado dela, para o alerta (verificar-backup.sh).
set -eu
umask 077
: "${SIGH_BACKUP_DIR:?defina SIGH_BACKUP_DIR}"
: "${SIGH_CHAVE_BACKUP:?defina SIGH_CHAVE_BACKUP}"

mkdir -p /etc/sigh-backup /etc/crontabs
env | grep -E '^(SIGH_|PG)' | sed "s/'/'\\\\''/g; s/=/='/; s/\$/'/; s/^/export /" > /etc/sigh-backup/ambiente
horario="${SIGH_BACKUP_HORARIO:-0 2}"
cat > /etc/crontabs/root <<CRON
$horario * * 0   . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-completo.sh >> /proc/1/fd/1 2>&1
$horario * * 1-6 . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-incremental.sh >> /proc/1/fd/1 2>&1
CRON
# Sem nenhum backup ainda (instalação nova), o completo roda já, e não só no domingo: até lá os
# incrementais falhariam e o alerta ficaria aceso a semana toda.
if [ ! -f "$SIGH_BACKUP_DIR/cadeia-atual" ]; then
    ( . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-completo.sh >> /proc/1/fd/1 2>&1 ) &
fi
echo "[sigh-backup] agenda instalada: completo aos domingos e incremental de segunda a sábado ($horario)" >&2
exec crond -f -l 6
