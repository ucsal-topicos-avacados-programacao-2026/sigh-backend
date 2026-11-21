# PostgreSQL 17 do SIGH, com as ferramentas de backup e o gpg para cifrar as cópias. A mesma imagem:
# - sem argumentos, sobe o agendador do backup (crond);
# - com iniciar-banco.sh, sobe o servidor do banco só com TLS e com o WAL arquivado cifrado;
# - também roda um script direto (restaurar.sh, verificar-backup.sh).
FROM postgres:17-alpine
RUN apk add --no-cache gnupg
COPY backup/ /opt/sigh-backup/
RUN chmod 0755 /opt/sigh-backup/*.sh
ENV PATH="/opt/sigh-backup:${PATH}"
# Alerta: o container fica unhealthy quando o backup ou o arquivamento de WAL falham.
HEALTHCHECK --interval=10m --timeout=1m --start-period=5m CMD ["/opt/sigh-backup/verificar-backup.sh"]
ENTRYPOINT []
CMD ["/opt/sigh-backup/agendador.sh"]
