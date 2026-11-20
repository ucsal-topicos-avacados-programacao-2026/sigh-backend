// Teste de carga do TST-04 (RNF-001 e RNF-002), com o k6.
//
// Cada usuário virtual é um profissional diferente dos dados de carga (carga/dados-de-carga.sql):
// - médicos (1-150): consultam o prontuário de um paciente em atendimento no Hospital A, com
//   registros do Hospital B; de vez em quando, olham a fila;
// - enfermeiros (151-190): alternam entre a fila e a consulta do prontuário;
// - administrativos (191-200): buscam paciente por CPF.
// Cada um faz login com senha e segundo fator no início (setup), como na aplicação de verdade.
//
// Uso: k6 run carga/carga.js
// Variáveis: SIGH_SENHA_CARGA, SIGH_MFA_CARGA (as mesmas dos dados), URL (padrão https://localhost:8443),
// USUARIOS (padrão 200), DURACAO (padrão 5m), PAUSA (pausa máxima entre operações, em segundos; padrão 3).

import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, sleep } from 'k6';

const URL = __ENV.URL || 'https://localhost:8443';
const USUARIOS = Number(__ENV.USUARIOS || 200);
const DURACAO = __ENV.DURACAO || '5m';
const PAUSA = Number(__ENV.PAUSA ?? 3);
const SENHA = __ENV.SIGH_SENHA_CARGA;
const SEGREDO_MFA = __ENV.SIGH_MFA_CARGA;
const PACIENTES = 2000;

export const options = {
	// Certificado fictício gerado só para o teste.
	insecureSkipTLSVerify: true,
	setupTimeout: '10m',
	scenarios: {
		profissionais: {
			executor: 'ramping-vus',
			startVUs: 0,
			stages: [
				{ duration: '1m', target: USUARIOS },
				{ duration: DURACAO, target: USUARIOS },
				{ duration: '30s', target: 0 },
			],
			gracefulRampDown: '30s',
		},
	},
	thresholds: {
		// RNF-001: a consulta ao prontuário termina em até 3 s.
		'http_req_duration{operacao:consulta_prontuario}': ['p(95)<3000', 'max<3000'],
		// Só para o resumo mostrar cada operação em separado.
		'http_req_duration{operacao:fila}': ['p(95)<3000'],
		'http_req_duration{operacao:busca_paciente}': ['p(95)<3000'],
		'http_req_duration{operacao:login}': ['p(95)<3000'],
		// RNF-002: sem falhas nas operações testadas.
		http_req_failed: ['rate==0'],
		checks: ['rate==1'],
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const INSTITUICAO_A = id('instituicao', 1);
const INSTITUICAO_B = id('instituicao', 2);

// O mesmo md5('carga:' || prefixo || ':' || n)::uuid dos dados de carga.
function id(prefixo, n) {
	const h = crypto.md5(`carga:${prefixo}:${n}`, 'hex');
	return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

// O mesmo CPF fictício dos dados de carga.
function cpf(n) {
	const base = String(100000000 + n * 37).padStart(9, '0');
	const digito = (texto, peso) => {
		let soma = 0;
		for (let i = 0; i < texto.length; i++) soma += Number(texto[i]) * (peso - i);
		return soma % 11 < 2 ? 0 : 11 - (soma % 11);
	};
	const d1 = digito(base, 10);
	return base + d1 + digito(base + d1, 11);
}

// Código do segundo fator (TOTP: HMAC-SHA1, 6 dígitos, 30 s), como num aplicativo autenticador.
function codigoDoSegundoFator(segredo) {
	const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
	let bits = '';
	for (const letra of segredo.replace(/=+$/, '')) bits += alfabeto.indexOf(letra).toString(2).padStart(5, '0');
	const chave = new Uint8Array(Math.floor(bits.length / 8));
	for (let i = 0; i < chave.length; i++) chave[i] = parseInt(bits.substr(i * 8, 8), 2);
	const contador = new Uint8Array(8);
	let passo = Math.floor(Date.now() / 30000);
	for (let i = 7; i >= 0; i--) {
		contador[i] = passo % 256;
		passo = Math.floor(passo / 256);
	}
	const h = new Uint8Array(crypto.hmac('sha1', chave.buffer, contador.buffer, 'binary'));
	const inicio = h[19] & 0xf;
	const valor = ((h[inicio] & 0x7f) << 24) | (h[inicio + 1] << 16) | (h[inicio + 2] << 8) | h[inicio + 3];
	return String(valor % 1000000).padStart(6, '0');
}

function perfil(n) {
	return n <= 150 ? 'medico' : n <= 190 ? 'enfermeiro' : 'administrativo';
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function entrar(n) {
	const login = `${perfil(n)}.carga.${String(n).padStart(3, '0')}`;
	const resposta = http.post(`${URL}/auth/login`, JSON.stringify({ login, senha: SENHA }),
		{ headers: JSON_HEADERS, tags: { operacao: 'login' } });
	if (resposta.status !== 200) throw new Error(`login de ${login}: ${resposta.status}`);
	const corpo = resposta.json();
	if (corpo.etapa === 'ACESSO_LIBERADO') return corpo.token;
	const validacao = http.post(`${URL}/auth/segundo-fator/validacao`,
		JSON.stringify({ codigo: codigoDoSegundoFator(SEGREDO_MFA) }),
		{ headers: { ...JSON_HEADERS, Authorization: `Bearer ${corpo.token}` }, tags: { operacao: 'login' } });
	if (validacao.status !== 200) throw new Error(`segundo fator de ${login}: ${validacao.status}`);
	return validacao.json('token');
}

export function setup() {
	if (!SENHA || !SEGREDO_MFA) throw new Error('Defina SIGH_SENHA_CARGA e SIGH_MFA_CARGA.');
	const tokens = [];
	for (let n = 1; n <= USUARIOS; n++) tokens.push(entrar(n));
	return { tokens };
}

function consultarProntuario(token) {
	const paciente = 1 + Math.floor(Math.random() * PACIENTES);
	const resposta = http.get(`${URL}/atendimentos/${id('atendimento-a', paciente)}/historico`,
		{ headers: { Authorization: `Bearer ${token}` }, tags: { operacao: 'consulta_prontuario' } });
	check(resposta, {
		'prontuário: 200': (r) => r.status === 200,
		'prontuário: 20 registros, com os do Hospital B': (r) => {
			const registros = r.status === 200 ? r.json('registros') : [];
			return registros.length === 20 && registros.some((registro) => registro.instituicaoOrigemId === INSTITUICAO_B);
		},
	});
}

function consultarFila(token) {
	const resposta = http.get(`${URL}/triagens/fila?instituicaoId=${INSTITUICAO_A}&size=20`,
		{ headers: { Authorization: `Bearer ${token}` }, tags: { operacao: 'fila' } });
	check(resposta, {
		'fila: 200': (r) => r.status === 200,
		'fila: 300 pacientes': (r) => r.status === 200 && r.json('page.totalElements') === 300,
	});
}

function buscarPaciente(token) {
	const paciente = 1 + Math.floor(Math.random() * PACIENTES);
	const resposta = http.post(`${URL}/api/pacientes/busca`,
		JSON.stringify({ cpf: cpf(paciente), instituicaoId: INSTITUICAO_A }),
		{ headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { operacao: 'busca_paciente' } });
	check(resposta, {
		'busca: 200': (r) => r.status === 200,
		'busca: paciente certo': (r) => r.status === 200 && r.json('id') === id('paciente', paciente),
	});
}

export default function (dados) {
	const n = ((__VU - 1) % USUARIOS) + 1;
	const token = dados.tokens[n - 1];
	const sorteio = Math.random();
	if (perfil(n) === 'medico') {
		if (sorteio < 0.8) consultarProntuario(token);
		else consultarFila(token);
	} else if (perfil(n) === 'enfermeiro') {
		if (sorteio < 0.5) consultarProntuario(token);
		else consultarFila(token);
	} else {
		buscarPaciente(token);
	}
	if (PAUSA > 0) sleep(Math.random() * PAUSA);
}
