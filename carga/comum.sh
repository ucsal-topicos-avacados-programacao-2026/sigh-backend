#!/bin/bash
# Funções comuns dos scripts de carga. Senha e segredo vêm do ambiente e nunca entram como argumento
# de comando, para não aparecerem na lista de processos.
URL=${URL:-https://localhost:8443}

# Cabeçalho de autorização para o curl ler de arquivo (-H @<(autorizacao "$token")): o token não vai no argv.
autorizacao() { printf 'Authorization: Bearer %s\n' "$1"; }

# O mesmo md5('carga:' || prefixo || ':' || n)::uuid dos dados de carga.
id_carga() { python3 -c "import hashlib,uuid,sys; print(uuid.UUID(hashlib.md5(('carga:'+sys.argv[1]+':'+sys.argv[2]).encode()).hexdigest()))" "$1" "$2"; }

# Código do segundo fator (TOTP: HMAC-SHA1, 6 dígitos, 30 s), a partir de SIGH_MFA_CARGA.
codigo_segundo_fator() { python3 -c "
import base64,hmac,os,struct,time
k=base64.b32decode(os.environ['SIGH_MFA_CARGA']); h=hmac.new(k,struct.pack('>Q',int(time.time())//30),'sha1').digest()
o=h[19]&15; print('%06d'%((struct.unpack('>I',h[o:o+4])[0]&0x7fffffff)%1000000))"; }

# entrar <login> [com-segundo-fator]: devolve o token de acesso.
entrar() {
	local token
	token=$(printf '{"login":"%s","senha":"%s"}' "$1" "$SIGH_SENHA_CARGA" \
		| curl -sk "$URL/auth/login" -H 'Content-Type: application/json' --data @- \
		| python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
	if [ "${2:-}" = "com-segundo-fator" ]; then
		token=$(curl -sk "$URL/auth/segundo-fator/validacao" -H 'Content-Type: application/json' \
			-H @<(autorizacao "$token") -d "{\"codigo\":\"$(codigo_segundo_fator)\"}" \
			| python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
	fi
	echo "$token"
}

# Resumo de uma lista de tempos em segundos (um por linha): mínimo, mediana, p95 e máximo.
estatisticas() { python3 -c "
import statistics,sys
t=sorted(float(x) for x in sys.stdin if x.strip())
if not t: print('nenhuma medição'); sys.exit()
p95=t[min(len(t)-1, round(0.95*(len(t)-1)))]
print(f'{len(t)} medições: mín {t[0]:.2f} s, mediana {statistics.median(t):.2f} s, p95 {p95:.2f} s, máx {t[-1]:.2f} s')"; }
