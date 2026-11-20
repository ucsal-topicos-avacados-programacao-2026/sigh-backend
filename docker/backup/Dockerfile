# Executor da rotina de backup do SIGH: ferramentas do PostgreSQL 17 + openssl para cifrar as cópias.
FROM postgres:17-alpine
RUN apk add --no-cache openssl
COPY backup/ /opt/sigh-backup/
RUN chmod 0755 /opt/sigh-backup/*.sh
ENV PATH="/opt/sigh-backup:${PATH}"
