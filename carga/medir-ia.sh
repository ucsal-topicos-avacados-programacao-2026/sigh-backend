#!/bin/bash
# Tempo das operações com IA (RNF-001: medidas separadamente da consulta comum), com o serviço de IA
# configurado (padrão: Ollama local com qwen3:8b), pela API de carga:
#  - resumo do histórico (RF-004): um médico pede o resumo de um paciente com 20 registros. O pedido volta
#    na hora (202) e o resumo é gerado em segundo plano (BE-25): o tempo vai do pedido até o resumo ficar
#    pronto, consultado a cada segundo em GET /resumos/{id};
#  - sugestão de risco na triagem (RF-003): um enfermeiro registra a triagem e pede a sugestão.
# Primeiro um pedido de aquecimento (carrega o modelo), depois N pedidos um após o outro e, por fim,
# CONCORRENTES pedidos de resumo ao mesmo tempo.
#
# Uso: carga/medir-ia.sh   Variáveis: SIGH_ENV, URL, N (padrão 10), CONCORRENTES (padrão 5).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . "${SIGH_ENV:-$HOME/.config/sigh/sigh-local.env}"; set +a
. carga/comum.sh
N=${N:-10}
CONCORRENTES=${CONCORRENTES:-5}
TEMPOS=$(mktemp -d)
trap 'rm -rf "$TEMPOS"' EXIT

# resumo <paciente>: "status-final segundos", do pedido até o resumo ficar pronto (GERADO) ou falhar.
# O médico responsável pelo paciente n é o 1 + n % 150.
resumo() {
	local medico token inicio codigo resumo_id situacao
	medico=$(printf 'medico.carga.%03d' $((1 + $1 % 150)))
	token=$(entrar "$medico" com-segundo-fator)
	inicio=$(python3 -c 'import time; print(time.time())')
	codigo=$(curl -sk -o "$TEMPOS/resposta-$1" -w '%{http_code}' -X POST \
		"$URL/atendimentos/$(id_carga atendimento-a "$1")/resumos" -H @<(autorizacao "$token"))
	if [ "$codigo" != 202 ]; then
		echo "HTTP-$codigo $(python3 -c "import time; print(round(time.time() - $inicio, 2))")"
		return
	fi
	resumo_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$TEMPOS/resposta-$1")
	# Consulta a cada segundo, por até 10 min. Uma resposta que não seja o resumo conta como ERRO.
	situacao=SOLICITADO
	for _ in $(seq 1 600); do
		sleep 1
		situacao=$(curl -sk "$URL/resumos/$resumo_id" -H @<(autorizacao "$token") \
			| python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null || echo ERRO)
		[ "$situacao" = SOLICITADO ] || break
	done
	[ "$situacao" = SOLICITADO ] && situacao=TEMPO-ESGOTADO
	echo "$situacao $(python3 -c "import time; print(round(time.time() - $inicio, 2))")"
}

# sugestao <paciente>: registra a triagem (fora da medição) e mede o pedido de sugestão.
sugestao() {
	local enfermeiro token triagem
	enfermeiro=$(printf 'enfermeiro.carga.%03d' $((151 + $1 % 40)))
	token=$(entrar "$enfermeiro" com-segundo-fator)
	triagem=$(curl -sk "$URL/triagens" -H 'Content-Type: application/json' -H @<(autorizacao "$token") \
		-d "{\"atendimentoId\":\"$(id_carga atendimento-a "$1")\",\"sintomas\":\"Dor torácica em aperto há 2 horas, com sudorese e falta de ar\",\"sinaisVitais\":{\"pressaoArterial\":\"150/95\",\"frequenciaCardiaca\":110,\"frequenciaRespiratoria\":24,\"temperatura\":36.8,\"saturacaoOxigenio\":93.0}}" \
		| python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
	curl -sk -o "$TEMPOS/sugestao-$1" -w '%{http_code} %{time_total}\n' -X POST "$URL/triagens/$triagem/sugestao" \
		-H @<(autorizacao "$token")
}

# conferir <resultado-esperado> < arquivo de linhas "resultado tempo": conta os pedidos que deram certo.
# Uma falha da IA (FALHOU, HTTP-503) não interrompe a medição: é o resultado que o profissional veria.
# O segundo argumento é quantos pedidos foram feitos: um pedido sem linha de resultado conta como SEM-RESULTADO.
conferir() {
	awk -v e="$1" -v total="$2" '$1 == e {ok++} $1 != e {falhas[$1]++} END {
		if (NR < total) falhas["SEM-RESULTADO"] += total - NR
		printf "  deram certo: %d de %d", ok, total; for (s in falhas) printf "; %s: %d", s, falhas[s]; print ""}'
}

echo "== Aquecimento (carrega o modelo na memória)"
# Com o Ollama nesta máquina, descarrega o modelo antes, para medir a partida a frio.
if command -v ollama >/dev/null; then ollama stop "${SIGH_IA_MODELO:-qwen3:8b}" 2>/dev/null || true; fi
echo "resumo: $(resumo 100 | awk '{print $1 " em " $2 " s"}')"

echo "== Resumo do histórico, $N pedidos um após o outro"
for n in $(seq 101 $((100 + N))); do resumo "$n"; done > "$TEMPOS/resumos"
conferir GERADO "$N" < "$TEMPOS/resumos"
awk '$1 == "GERADO" {print $2}' "$TEMPOS/resumos" | estatisticas

echo "== Sugestão de risco na triagem, $N pedidos um após o outro"
for n in $(seq 1701 $((1700 + N))); do sugestao "$n"; done > "$TEMPOS/sugestoes"
conferir 200 "$N" < "$TEMPOS/sugestoes"
awk '$1 == 200 {print $2}' "$TEMPOS/sugestoes" | estatisticas

echo "== Resumo do histórico, $CONCORRENTES pedidos ao mesmo tempo"
for n in $(seq 201 $((200 + CONCORRENTES))); do resumo "$n" > "$TEMPOS/concorrente-$n" & done
wait
cat "$TEMPOS"/concorrente-* > "$TEMPOS/concorrentes"
conferir GERADO "$CONCORRENTES" < "$TEMPOS/concorrentes"
echo "  tempo de cada pedido: $(cut -d' ' -f2 "$TEMPOS/concorrentes" | sort -n | awk '{printf "%.1f s ", $1}')"
