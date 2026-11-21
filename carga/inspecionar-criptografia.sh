#!/bin/bash
# Inspeção da criptografia do TST-04, na forma de verificação do RNF-004: transporte, armazenamento,
# acesso às chaves e pontos de decifragem, cada um separadamente, no ambiente de carga.
#
# Um médico grava pela API um registro com um marcador único no texto clínico e um controle na
# categoria, que não é dado clínico e fica em claro. Depois, o script procura o marcador onde ele não
# pode estar em claro (tabelas e WAL do banco, log da API) e onde ele deve aparecer (a resposta da API
# ao profissional autorizado). O controle, gravado no mesmo momento, prova que a busca acha o que existe.
# Também procura o texto clínico dos dados de carga, que entrou em claro e foi cifrado ao subir.
#
# Uso: carga/inspecionar-criptografia.sh <log-da-api>   (com a API de carga no ar)
# Variáveis: SIGH_ENV, URL (padrão https://localhost:8443), SIGH_BANCO_CARGA, SIGH_CONTAINER_ORIGEM.
set -euo pipefail
cd "$(dirname "$0")/.."
LOG_API=${1:?informe o arquivo de log da API}
set -a; . "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"; set +a
BANCO=${SIGH_BANCO_CARGA:-sigh_carga}
CONTAINER=${SIGH_CONTAINER_ORIGEM:-sigh-db}
PGDATA_CONTAINER=/var/lib/postgresql/data
MARCADOR="MarcadorTST04$(openssl rand -hex 6)"
CONTROLE="ControleTST04$(openssl rand -hex 6)"
# Texto que existe em todo registro clínico dos dados de carga, só em ASCII: grep em bytes (LC_ALL=C).
TEXTO_DA_CARGA="esquerdo, sudorese e n"
. carga/comum.sh
# Paciente 1 dos dados de carga: atendimento atual no Hospital A, com o médico 002 responsável.
ATENDIMENTO=$(id_carga atendimento-a 1)
sql() { docker exec "$CONTAINER" psql -U postgres -d "$BANCO" -Atc "$1"; }
procurar() { docker exec "$CONTAINER" sh -c "LC_ALL=C grep -r -a -l -F '$1' $2 | wc -l" | tr -d ' '; }
: "${SIGH_CHAVE_DADOS:?a inspeção das chaves precisa de SIGH_CHAVE_DADOS no ambiente}"

echo "== Transporte"
echo "cliente -> API: $(openssl s_client -connect "${URL#https://}" -brief </dev/null 2>&1 | grep -E 'Protocol version|Ciphersuite' | tr '\n' ' ')"
echo "HSTS: $(curl -skI "$URL/auth/login" | grep -i '^strict-transport-security' | tr -d '\r' || echo 'ausente')"
# O Tomcat responde 400 ("requires TLS") a HTTP em claro, sem chegar à aplicação.
em_claro=$(curl -s --max-time 5 -w ' HTTP %{http_code}' "http://${URL#https://}/auth/login" 2>/dev/null | tr '\n' ' ' || true)
case "$em_claro" in
	*"requires TLS"*"HTTP 400"*) echo "HTTP em claro na porta da API: recusado (HTTP 400, a porta exige TLS)" ;;
	*) echo "HTTP em claro na porta da API: ACEITO ($em_claro)" ;;
esac
echo "API -> banco neste ambiente: TLS=$(sql "select coalesce(bool_or(s.ssl), false) from pg_stat_ssl s join pg_stat_activity a using (pid) where a.datname = '$BANCO' and a.application_name like 'PostgreSQL JDBC%'")"

echo "== Ponto de decifragem: a API"
MEDICO=$(entrar medico.carga.002 com-segundo-fator)
ADMINISTRATIVO=$(entrar administrativo.carga.191 sem-segundo-fator)
status=$(curl -sk -o /dev/null -w '%{http_code}' "$URL/atendimentos/$ATENDIMENTO/registros" \
	-H 'Content-Type: application/json' -H @<(autorizacao "$MEDICO") \
	-d "{\"tipo\":\"EVOLUCAO\",\"conteudo\":\"$MARCADOR evolução fictícia da inspeção\",\"categoriaInformacao\":\"$CONTROLE\"}")
echo "médico grava pela API um registro com o marcador: HTTP $status"
echo "médico autorizado lê o marcador em claro: $(curl -sk "$URL/atendimentos/$ATENDIMENTO/historico" -H @<(autorizacao "$MEDICO") | grep -c "$MARCADOR" || true) resposta(s) com o marcador"
echo "administrativo, sem perfil clínico, consulta o mesmo prontuário: HTTP $(curl -sk -o /dev/null -w '%{http_code}' "$URL/atendimentos/$ATENDIMENTO/historico" -H @<(autorizacao "$ADMINISTRATIVO"))"

echo "== Armazenamento"
sql "checkpoint" >/dev/null
echo "valor gravado no banco: $(sql "select left(conteudo, 24) || '...' from registro_clinico where atendimento_id = '$ATENDIMENTO' order by criado_em desc limit 1")"
echo "valores fora do formato cifrado:"
for coluna in registro_clinico.conteudo registro_clinico.codigo_cid10 atendimento.motivo triagem.sintomas \
	triagem.pressao_arterial triagem.frequencia_cardiaca triagem.temperatura triagem.saturacao_oxigenio usuario.mfa_segredo; do
	tabela=${coluna%.*}; campo=${coluna#*.}
	echo "  $coluna: $(sql "select count(*) filter (where $campo is not null and $campo not like 'sigh:v2:%') || ' de ' || count($campo) from $tabela")"
done
for pasta in base pg_wal; do
	echo "em $pasta/: controle (categoria, gravada junto) em $(procurar "$CONTROLE" "$PGDATA_CONTAINER/$pasta") arquivo(s);" \
		"marcador clínico em $(procurar "$MARCADOR" "$PGDATA_CONTAINER/$pasta");" \
		"texto clínico da carga em $(procurar "$TEXTO_DA_CARGA" "$PGDATA_CONTAINER/$pasta")"
done
echo "marcador no log da API: $(grep -c "$MARCADOR" "$LOG_API" || true) linha(s)"

echo "== Volume ao fim"
echo "banco: $(sql "select pg_size_pretty(pg_database_size('$BANCO'))"), $(sql "select count(*) from registro_auditoria") registros de auditoria"

echo "== Chaves"
# A chave entra nas buscas pela entrada padrão, nunca como argumento: assim ela não aparece na lista de processos.
chave() { printf '%s\n' "$SIGH_CHAVE_DADOS"; }
PID=$(pgrep -f sigh-project-0.0.1-SNAPSHOT.jar | head -1)
echo "chave dos dados na linha de comando da API: $(ps -o command= -p "$PID" | grep -c -F -f <(chave) || true)"
echo "chave dos dados no ambiente do banco: $(docker exec "$CONTAINER" env | grep -c -F -f <(chave) || true)"
echo "chave dos dados nos arquivos do banco: $(chave | docker exec -i "$CONTAINER" sh -c "LC_ALL=C grep -r -a -l -F -f - $PGDATA_CONTAINER | wc -l" | tr -d ' ') arquivo(s)"
echo "chave dos dados no repositório (inclusive o histórico do git): $(git log --all -p | grep -c -F -f <(chave) || true) ocorrência(s)"
echo "arquivo de ambiente: $(stat -f '%Sp' "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"), fora do repositório"
