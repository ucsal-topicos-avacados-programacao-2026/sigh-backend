# Executor da rotina de backup do SIGH: ferramentas do PostgreSQL 17 + gpg para cifrar as cópias.
# Sem argumentos, sobe o agendador (crond); também aceita rodar um script direto.
FROM postgres:17-alpine
RUN apk add --no-cache gnupg
COPY backup/ /opt/sigh-backup/
RUN chmod 0755 /opt/sigh-backup/*.sh
ENV PATH="/opt/sigh-backup:${PATH}"
ENTRYPOINT []
CMD ["/opt/sigh-backup/agendador.sh"]
