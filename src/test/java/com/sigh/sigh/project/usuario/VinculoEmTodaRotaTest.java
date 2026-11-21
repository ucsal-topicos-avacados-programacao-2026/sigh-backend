package com.sigh.sigh.project.usuario;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-20 (RF-012): toda rota de médico, enfermeiro ou administrativo exige vínculo ativo com a
 * instituição dos dados. As rotas vêm do próprio Spring, não de uma lista escrita à mão: uma rota nova
 * sem caso aqui faz o teste falhar, e o caso dela só passa se quem não tem vínculo receber 403.
 *
 * <p>Para cada rota e para cada perfil de instituição que ela aceita:
 * <ul>
 * <li>um usuário com o perfil e sem vínculo nenhum recebe 403;</li>
 * <li>um usuário com o perfil e vínculo só em outra instituição recebe 403;</li>
 * <li>um usuário com vínculo na instituição dos dados, mas encerrado ou com outro perfil, recebe 403;</li>
 * <li>controle positivo: um usuário com vínculo na instituição dos dados, com a mesma requisição, não
 * recebe 401 nem 403. A única diferença entre eles é o vínculo, então o 403 vem da checagem de vínculo, e
 * não de uma requisição mal montada.</li>
 * </ul>
 * Toda requisição precisa cair no método da rota do caso (e não em outra rota parecida).
 * Cada caso usa um cenário novo, para uma rota não interferir na outra.
 */
class VinculoEmTodaRotaTest extends TesteIntegracao {

	private static final Set<String> PERFIS_DE_INSTITUICAO = Set.of("MEDICO", "ENFERMEIRO", "ADMINISTRATIVO", "RECEPCAO");
	private static final Pattern PERFIL = Pattern.compile("'([A-Z_]+)'");
	/**
	 * As únicas regras de acesso que o teste sabe ler. Qualquer outra (hasAuthority, SpEL com bean,
	 * isAuthenticated...) faz o teste falhar, em vez de a rota sumir da conferência sem aviso.
	 */
	private static final Pattern REGRA_RECONHECIDA = Pattern.compile("has(Any)?Role\\('[A-Z_]+'(\\s*,\\s*'[A-Z_]+')*\\)");

	/** Rotas de perfil de instituição que, de propósito, não exigem vínculo, e por quê. */
	private static final Map<String, String> SEM_VINCULO = Map.of(
			"GET /leitos", "busca de leitos aberta à rede, para ver vagas em transferências; não traz dado de"
					+ " paciente (BE-13)",
			"GET /api/instituicoes", "lista de instituições credenciadas da rede (só id e nome), para escolher o"
					+ " destino de uma transferência; não traz dado de paciente (FE-04)",
			"GET /usuarios/eu", "dados do próprio usuário logado, inclusive os vínculos dele: é com isso que o"
					+ " front-end sabe em que instituição ele pode operar; não traz dado de paciente (FE-02)");

	/** Rotas sem @PreAuthorize: só as de autenticação, protegidas pela configuração de segurança. */
	private static final Set<String> SEM_PREAUTHORIZE = Set.of("POST /auth/login", "POST /auth/segundo-fator/cadastro",
			"POST /auth/segundo-fator/validacao");

	private static final String REGISTRO = """
			{"tipo":"DIAGNOSTICO","conteudo":"Pneumonia","categoriaInformacao":"Clínica geral"}
			""";
	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Febre","sinaisVitais":{"pressaoArterial":"120/80","frequenciaCardiaca":90,
			 "frequenciaRespiratoria":18,"temperatura":38.5,"saturacaoOxigenio":96.0}}
			""";

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapeamento;

	@Autowired
	ResumoHistoricoService resumoHistoricoService;

	@Autowired
	JdbcTemplate jdbc;

	/** Dados de um hospital, com tudo o que as rotas usam. */
	private record Dados(Instituicao hospital, String cpf, UUID pacienteId, UUID atendimentoId, UUID triagemId,
			UUID resumoId, UUID atendimentoSemTriagemId, UUID leitoId, UUID leitoLivreId, UUID internacaoId,
			UUID representacaoId, Instituicao outraInstituicao, UUID solicitacaoEnviadaId, UUID solicitacaoRecebidaId) {
	}

	/** A requisição de cada rota, montada sobre os dados do hospital. */
	private static Map<String, Function<Dados, MockHttpServletRequestBuilder>> casos() {
		Map<String, Function<Dados, MockHttpServletRequestBuilder>> casos = new LinkedHashMap<>();
		casos.put("POST /atendimentos/{atendimentoId}/registros",
				d -> post("/atendimentos/{id}/registros", d.atendimentoId()).content(REGISTRO));
		casos.put("POST /atendimentos/{atendimentoId}/encerramento",
				d -> post("/atendimentos/{id}/encerramento", d.atendimentoId()));
		casos.put("GET /atendimentos/{atendimentoId}/historico", d -> get("/atendimentos/{id}/historico", d.atendimentoId()));
		casos.put("POST /atendimentos/{atendimentoId}/historico/emergencia",
				d -> post("/atendimentos/{id}/historico/emergencia", d.atendimentoId())
						.content("{\"justificativa\":\"Paciente inconsciente, sem acompanhante, na sala vermelha\"}"));
		casos.put("POST /atendimentos/{atendimentoId}/resumos", d -> post("/atendimentos/{id}/resumos", d.atendimentoId()));
		casos.put("GET /resumos/{resumoId}", d -> get("/resumos/{id}", d.resumoId()));
		casos.put("POST /resumos/{resumoId}/validacao", d -> post("/resumos/{id}/validacao", d.resumoId()));
		casos.put("POST /triagens", d -> post("/triagens").content(TRIAGEM.formatted(d.atendimentoSemTriagemId())));
		casos.put("POST /triagens/{triagemId}/sugestao", d -> post("/triagens/{id}/sugestao", d.triagemId()));
		casos.put("PATCH /triagens/{triagemId}/sinais-vitais", d -> patch("/triagens/{id}/sinais-vitais", d.triagemId())
				.content("{\"sinaisVitais\":{\"frequenciaCardiaca\":90}}"));
		casos.put("GET /triagens/pre-classificacao", d -> get("/triagens/pre-classificacao")
				.param("atendimentoId", d.atendimentoId().toString()).param("instituicaoId", d.hospital().getId().toString()));
		casos.put("POST /triagens/{triagemId}/confirmacao",
				d -> post("/triagens/{id}/confirmacao", d.triagemId()).content("{\"cor\":\"VERDE\"}"));
		casos.put("GET /triagens/fila", d -> get("/triagens/fila").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("GET /atendimentos/em-andamento",
				d -> get("/atendimentos/em-andamento").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("GET /painel", d -> get("/painel").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("GET /triagens/aguardando", d -> get("/triagens/aguardando").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("POST /leitos", d -> post("/leitos").content("""
				{"instituicaoId":"%s","codigo":"N-%s","setor":"Enfermaria","categoria":"ENFERMARIA"}
				""".formatted(d.hospital().getId(), UUID.randomUUID().toString().substring(0, 8))));
		casos.put("PATCH /leitos/{leitoId}/status",
				d -> patch("/leitos/{id}/status", d.leitoLivreId()).content("{\"status\":\"BLOQUEADO\"}"));
		casos.put("POST /internacoes", d -> post("/internacoes").content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}"
				.formatted(d.atendimentoSemTriagemId(), d.leitoLivreId())));
		casos.put("POST /internacoes/{internacaoId}/transferencia",
				d -> post("/internacoes/{id}/transferencia", d.internacaoId())
						.content("{\"leitoDestinoId\":\"%s\"}".formatted(d.leitoLivreId())));
		casos.put("GET /internacoes", d -> get("/internacoes").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("GET /internacoes/atendimentos-sem-leito",
				d -> get("/internacoes/atendimentos-sem-leito").param("instituicaoId", d.hospital().getId().toString()));
		casos.put("POST /internacoes/{internacaoId}/alta", d -> post("/internacoes/{id}/alta", d.internacaoId())
				.content("{\"resumoAltaId\":\"%s\"}".formatted(UUID.randomUUID())));
		casos.put("POST /internacoes/{internacaoId}/solicitacoes-transferencia",
				d -> post("/internacoes/{id}/solicitacoes-transferencia", d.internacaoId()).content(
						"{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"ENFERMARIA\"}".formatted(d.outraInstituicao().getId())));
		casos.put("GET /solicitacoes-transferencia/recebidas", d -> get("/solicitacoes-transferencia/recebidas")
				.param("instituicaoId", d.hospital().getId().toString()));
		casos.put("GET /solicitacoes-transferencia/enviadas", d -> get("/solicitacoes-transferencia/enviadas")
				.param("instituicaoId", d.hospital().getId().toString()));
		casos.put("POST /solicitacoes-transferencia/{solicitacaoId}/aceite",
				d -> post("/solicitacoes-transferencia/{id}/aceite", d.solicitacaoRecebidaId())
						.content("{\"leitoId\":\"%s\"}".formatted(d.leitoLivreId())));
		casos.put("POST /solicitacoes-transferencia/{solicitacaoId}/recusa",
				d -> post("/solicitacoes-transferencia/{id}/recusa", d.solicitacaoRecebidaId())
						.content("{\"motivo\":\"Sem vaga na equipe\"}"));
		casos.put("POST /solicitacoes-transferencia/{solicitacaoId}/cancelamento",
				d -> post("/solicitacoes-transferencia/{id}/cancelamento", d.solicitacaoEnviadaId()));
		casos.put("POST /api/pacientes/busca", d -> post("/api/pacientes/busca")
				.content("{\"cpf\":\"%s\",\"instituicaoId\":\"%s\"}".formatted(d.cpf(), d.hospital().getId())));
		casos.put("POST /api/pacientes/admissoes", d -> post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"João Fictício","dataNascimento":"1975-03-02","instituicaoId":"%s","motivo":"Dor"}
				""".formatted(Cenario.novoCpf(), d.hospital().getId())));
		casos.put("POST /api/pacientes/{pacienteId}/conta", d -> post("/api/pacientes/{id}/conta", d.pacienteId())
				.content("{\"instituicaoId\":\"%s\",\"login\":\"conta-%s\"}".formatted(d.hospital().getId(), UUID.randomUUID())));
		casos.put("GET /api/pacientes/{pacienteId}/representantes", d -> get("/api/pacientes/{id}/representantes",
				d.pacienteId()).param("instituicaoId", d.hospital().getId().toString()));
		casos.put("POST /api/pacientes/{pacienteId}/representantes", d -> post("/api/pacientes/{id}/representantes",
				d.pacienteId()).content("{\"instituicaoId\":\"%s\",\"login\":\"representante-%s\",\"documento\":\"Procuração\"}"
				.formatted(d.hospital().getId(), UUID.randomUUID())));
		casos.put("POST /api/pacientes/{pacienteId}/representantes/{representacaoId}/encerramento",
				d -> post("/api/pacientes/{id}/representantes/{representacao}/encerramento", d.pacienteId(),
						d.representacaoId()).content("{\"instituicaoId\":\"%s\"}".formatted(d.hospital().getId())));
		return casos;
	}

	/**
	 * Critério de aceite: uma rota nova de profissional sem caso aqui (nem justificativa em SEM_VINCULO)
	 * faz o teste falhar. E nenhuma rota fica sem @PreAuthorize, senão qualquer perfil a acessaria.
	 */
	@Test
	void todaRotaDeProfissionalTemCasoNoTeste() {
		Map<String, Set<String>> deProfissional = rotasDeProfissional();
		Set<String> cobertas = new TreeSet<>(casos().keySet());
		cobertas.addAll(SEM_VINCULO.keySet());

		assertThat(deProfissional.keySet())
				.as("rota de médico, enfermeiro ou administrativo sem caso no VinculoEmTodaRotaTest: acrescente o caso"
						+ " (e a checagem de vínculo no serviço) ou justifique em SEM_VINCULO")
				.containsExactlyInAnyOrderElementsOf(cobertas);
		assertThat(rotasSemPreAuthorize()).as("rota sem @PreAuthorize").isEqualTo(new TreeSet<>(SEM_PREAUTHORIZE));
	}

	@TestFactory
	Stream<DynamicTest> semVinculoComAInstituicaoTodaRotaDeProfissionalResponde403() {
		Map<String, Set<String>> perfisPorRota = rotasDeProfissional();
		Map<String, Method> metodos = metodosPorRota();
		return casos().entrySet().stream().flatMap(caso -> perfisPorRota.getOrDefault(caso.getKey(), Set.of()).stream()
				.map(perfil -> dynamicTest(caso.getKey() + " como " + perfil, () -> conferir(caso.getValue(),
						metodos.get(caso.getKey()), perfil, perfisPorRota.get(caso.getKey())))));
	}

	private void conferir(Function<Dados, MockHttpServletRequestBuilder> requisicao, Method rota, String perfil,
			Set<String> perfisDaRota) throws Exception {
		relogio.definir(INICIO);
		LocalDateTime agora = LocalDateTime.now(relogio);
		Dados dados = montarHospital();
		Instituicao outroHospital = cenario.instituicao(true);
		// Perfil de instituição que a rota não aceita (se ela aceitar todos, essa variante não se aplica).
		Optional<Perfil> outroPerfil = Arrays.stream(Perfil.values())
				.filter(candidato -> PERFIS_DE_INSTITUICAO.contains(candidato.name()))
				.filter(candidato -> !perfisDaRota.contains(candidato.name())).findFirst();
		Map<String, String> recusados = new LinkedHashMap<>();
		recusados.put("sem vínculo nenhum", cenario.usuario(Perfil.valueOf(perfil)).getLogin());
		recusados.put("com vínculo em outro hospital", cenario.profissional(outroHospital, Perfil.valueOf(perfil)));
		recusados.put("com vínculo encerrado no hospital dos dados",
				cenario.vinculo(dados.hospital(), Perfil.valueOf(perfil), agora.minusYears(1), agora.minusDays(1)));
		outroPerfil.ifPresent(outro -> recusados.put("com vínculo no hospital dos dados, mas como " + outro,
				cenario.profissional(dados.hospital(), outro)));
		String doHospital = cenario.profissional(dados.hospital(), Perfil.valueOf(perfil));

		for (Map.Entry<String, String> usuario : recusados.entrySet()) {
			assertThat(status(usuario.getValue(), perfil, requisicao.apply(dados), rota)).as(usuario.getKey()).isEqualTo(403);
		}
		assertThat(status(doHospital, perfil, requisicao.apply(dados), rota))
				.as("controle: com vínculo no hospital dos dados, a mesma requisição não é recusada").isNotIn(401, 403);
	}

	private int status(String login, String perfil, MockHttpServletRequestBuilder requisicao, Method rota) throws Exception {
		MvcResult resultado = mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)).andReturn();
		assertThat(resultado.getHandler()).as("a requisição do caso chega a uma rota da aplicação")
				.isInstanceOf(HandlerMethod.class);
		assertThat(((HandlerMethod) resultado.getHandler()).getMethod()).as("a requisição do caso cai na rota da chave")
				.isEqualTo(rota);
		return resultado.getResponse().getStatus();
	}

	/**
	 * Um hospital com paciente atendido, triagem, resumo, leitos, internação, representação legal e pedidos
	 * de transferência de e para outra instituição.
	 */
	private Dados montarHospital() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String administrativo = cenario.administrativo(hospital);
		String recepcao = cenario.recepcao(hospital);
		String enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);
		String medico = cenario.profissional(hospital, Perfil.MEDICO);
		String cpf = Cenario.novoCpf();
		MvcResult admissao = executar(recepcao, "RECEPCAO", post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
				 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Febre"}
				""".formatted(cpf, hospital.getId())));
		UUID pacienteId = id(admissao, "$.pacienteId");
		UUID atendimentoId = id(admissao, "$.atendimentoId");
		cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Asma");
		UUID triagemId = id(executar(enfermeiro, "ENFERMEIRO", post("/triagens").content(TRIAGEM.formatted(atendimentoId))), "$.id");
		UUID resumoId = resumoHistoricoService.solicitar(atendimentoId, medico).resumoId();
		UUID atendimentoSemTriagem = segundaAdmissao(hospital);
		UUID leito = leito(administrativo, hospital);
		UUID leitoLivre = leito(administrativo, hospital);
		UUID internado = segundaAdmissao(hospital);
		UUID internacaoId = id(executar(administrativo, "ADMINISTRATIVO", post("/internacoes")
				.content("{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(internado, leito))), "$.id");
		cenario.representante(pacienteId, LocalDateTime.now(relogio).minusDays(1), null);
		UUID representacaoId = jdbc.queryForObject("select id from representacao_legal where paciente_id = ?", UUID.class,
				pacienteId);
		// Transferência entre instituições: um pedido deste hospital para outro, e um de outro para este.
		Instituicao outra = cenario.instituicao(true);
		String administrativoDaOutra = cenario.administrativo(outra);
		UUID internacaoNaOutra = id(executar(administrativoDaOutra, "ADMINISTRATIVO", post("/internacoes").content(
				"{\"atendimentoId\":\"%s\",\"leitoId\":\"%s\"}".formatted(segundaAdmissao(outra),
						leito(administrativoDaOutra, outra)))), "$.id");
		UUID recebida = id(executar(administrativoDaOutra, "ADMINISTRATIVO", post("/internacoes/{id}/solicitacoes-transferencia",
				internacaoNaOutra).content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"ENFERMARIA\"}"
				.formatted(hospital.getId()))), "$.id");
		UUID enviada = id(executar(administrativo, "ADMINISTRATIVO", post("/internacoes/{id}/solicitacoes-transferencia",
				internacaoId).content("{\"instituicaoDestinoId\":\"%s\",\"categoria\":\"ENFERMARIA\"}"
				.formatted(outra.getId()))), "$.id");
		return new Dados(hospital, cpf, pacienteId, atendimentoId, triagemId, resumoId, atendimentoSemTriagem, leito,
				leitoLivre, internacaoId, representacaoId, outra, enviada, recebida);
	}

	private UUID segundaAdmissao(Instituicao hospital) throws Exception {
		return id(executar(cenario.recepcao(hospital), "RECEPCAO", post("/api/pacientes/admissoes").content("""
				{"cpf":"%s","nome":"Paciente Fictício","dataNascimento":"1990-01-01","instituicaoId":"%s","motivo":"Tosse"}
				""".formatted(Cenario.novoCpf(), hospital.getId()))), "$.atendimentoId");
	}

	private UUID leito(String administrativo, Instituicao hospital) throws Exception {
		return id(executar(administrativo, "ADMINISTRATIVO", post("/leitos").content("""
				{"instituicaoId":"%s","codigo":"L-%s","setor":"Enfermaria","categoria":"ENFERMARIA"}
				""".formatted(hospital.getId(), UUID.randomUUID().toString().substring(0, 8)))), "$.id");
	}

	private MvcResult executar(String login, String perfil, MockHttpServletRequestBuilder requisicao) throws Exception {
		MvcResult resultado = mvc.perform(requisicao.with(user(login).roles(perfil)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)).andReturn();
		assertThat(resultado.getResponse().getStatus()).as("montagem do cenário: " + resultado.getResponse()
				.getContentAsString()).isBetween(200, 299);
		return resultado;
	}

	/**
	 * Toda regra de acesso das rotas da aplicação é uma que o teste sabe ler, e toda rota declara o método
	 * HTTP. Senão a rota sumiria das conferências acima sem aviso.
	 */
	@Test
	void todaRotaTemRegraDeAcessoReconhecidaEMetodoHttp() {
		Set<String> naoReconhecidas = new TreeSet<>();
		Set<String> semMetodoHttp = new TreeSet<>();
		mapeamento.getHandlerMethods().forEach((info, metodo) -> {
			if (!daAplicacao(metodo)) {
				return;
			}
			PreAuthorize regra = preAuthorize(metodo);
			if (regra != null && !REGRA_RECONHECIDA.matcher(regra.value().strip()).matches()) {
				naoReconhecidas.add(metodo.getShortLogMessage() + ": " + regra.value());
			}
			if (info.getMethodsCondition().getMethods().isEmpty()) {
				semMetodoHttp.add(metodo.getShortLogMessage());
			}
		});

		assertThat(naoReconhecidas).as("regra de acesso que o teste não sabe ler: use hasRole/hasAnyRole ou adapte o"
				+ " VinculoEmTodaRotaTest").isEmpty();
		assertThat(semMetodoHttp).as("rota sem método HTTP declarado (aceitaria qualquer verbo)").isEmpty();
	}

	private Map<String, Method> metodosPorRota() {
		Map<String, Method> metodos = new TreeMap<>();
		mapeamento.getHandlerMethods().forEach((info, metodo) -> chaves(info).forEach(chave -> metodos.put(chave,
				metodo.getMethod())));
		return metodos;
	}

	/** "MÉTODO /caminho" de cada rota da aplicação que aceita um perfil de instituição, com esses perfis. */
	private Map<String, Set<String>> rotasDeProfissional() {
		Map<String, Set<String>> rotas = new TreeMap<>();
		mapeamento.getHandlerMethods().forEach((info, metodo) -> {
			if (!daAplicacao(metodo)) {
				return;
			}
			Set<String> perfis = new TreeSet<>(perfis(metodo));
			perfis.retainAll(PERFIS_DE_INSTITUICAO);
			if (!perfis.isEmpty()) {
				chaves(info).forEach(chave -> rotas.put(chave, perfis));
			}
		});
		return rotas;
	}

	private Set<String> rotasSemPreAuthorize() {
		Set<String> rotas = new TreeSet<>();
		mapeamento.getHandlerMethods().forEach((info, metodo) -> {
			if (daAplicacao(metodo) && preAuthorize(metodo) == null) {
				rotas.addAll(chaves(info));
			}
		});
		return rotas;
	}

	private static boolean daAplicacao(HandlerMethod metodo) {
		return metodo.getBeanType().getPackageName().startsWith("com.sigh.sigh.project");
	}

	private static PreAuthorize preAuthorize(HandlerMethod metodo) {
		PreAuthorize doMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
		return doMetodo != null ? doMetodo : AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
	}

	private static Set<String> perfis(HandlerMethod metodo) {
		Set<String> perfis = new TreeSet<>();
		PreAuthorize regra = preAuthorize(metodo);
		if (regra != null) {
			Matcher perfil = PERFIL.matcher(regra.value());
			while (perfil.find()) {
				perfis.add(perfil.group(1));
			}
		}
		return perfis;
	}

	private static Set<String> chaves(RequestMappingInfo info) {
		Set<String> chaves = new TreeSet<>();
		for (var metodo : info.getMethodsCondition().getMethods()) {
			for (String caminho : info.getPathPatternsCondition().getPatternValues()) {
				chaves.add(metodo.name() + " " + caminho);
			}
		}
		return chaves;
	}

	private static UUID id(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
