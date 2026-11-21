package com.sigh.sigh.project.paciente;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdmissaoPacienteTest extends TesteIntegracao {

	@Autowired
	PacienteRepository pacienteRepository;

	@Autowired
	ProntuarioRepository prontuarioRepository;

	@Autowired
	AtendimentoRepository atendimentoRepository;

	@Autowired
	ConsentimentoRepository consentimentoRepository;

	@Autowired
	TransactionTemplate transacao;

	@Test
	void primeiroCadastroCriaPacienteProntuarioConsentimentoEAtendimento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		String cpf = Cenario.novoCpf();

		MvcResult resultado = mvc.perform(admissao(admin, novoCadastro(cpf, hospital.getId(), "AUTORIZADO")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.novoCadastro").value(true))
				.andReturn();

		UUID pacienteId = uuid(resultado, "$.pacienteId");
		UUID prontuarioId = uuid(resultado, "$.prontuarioId");
		UUID atendimentoId = uuid(resultado, "$.atendimentoId");

		Paciente paciente = pacienteRepository.findByCpf(cpf).orElseThrow();
		assertThat(paciente.getId()).isEqualTo(pacienteId);
		assertThat(paciente.getNome()).isEqualTo("Maria Fictícia");
		assertThat(paciente.getDataNascimento()).isEqualTo(LocalDate.of(1980, 5, 17));
		assertThat(prontuarioRepository.findByPacienteId(pacienteId).orElseThrow().getId()).isEqualTo(prontuarioId);

		List<Consentimento> consentimentos = consentimentoRepository.historico(pacienteId);
		assertThat(consentimentos).hasSize(1);
		Consentimento inicial = consentimentos.getFirst();
		assertThat(inicial.getVersao()).isEqualTo(1);
		assertThat(inicial.getTipo()).isEqualTo(TipoConsentimento.AMPLO);
		assertThat(inicial.getDecisao()).isEqualTo(DecisaoConsentimento.AUTORIZADO);
		assertThat(inicial.getVigenteDesde()).isEqualTo(LocalDateTime.ofInstant(INICIO, RelogioDeTeste.FUSO));

		transacao.executeWithoutResult(s -> {
			Atendimento atendimento = atendimentoRepository.findById(atendimentoId).orElseThrow();
			assertThat(atendimento.getProntuario().getId()).isEqualTo(prontuarioId);
			assertThat(atendimento.getInstituicao().getId()).isEqualTo(hospital.getId());
			assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.ABERTO);
			assertThat(atendimento.getChegadaEm()).isEqualTo(LocalDateTime.ofInstant(INICIO, RelogioDeTeste.FUSO));
			assertThat(atendimento.getMotivo()).isEqualTo("Dor no peito");
		});
	}

	@Test
	void mesmoCpfEmOutraInstituicaoReaproveitaOProntuario() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		String adminA = cenario.recepcao(hospitalA);
		String adminB = cenario.recepcao(hospitalB);
		String cpf = Cenario.novoCpf();

		MvcResult primeiro = mvc.perform(admissao(adminA, novoCadastro(cpf, hospitalA.getId(), "AUTORIZADO")))
				.andExpect(status().isCreated()).andReturn();

		String segundaAdmissao = """
				{"cpf":"%s","nome":"Outro Nome","dataNascimento":"1999-01-01","consentimentoInicial":"NEGADO",
				 "instituicaoId":"%s","motivo":"Retorno"}
				""".formatted(Cenario.comMascara(cpf), hospitalB.getId());
		MvcResult segundo = mvc.perform(admissao(adminB, segundaAdmissao))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.novoCadastro").value(false))
				.andReturn();

		UUID pacienteId = uuid(primeiro, "$.pacienteId");
		assertThat(uuid(segundo, "$.pacienteId")).isEqualTo(pacienteId);
		assertThat(uuid(segundo, "$.prontuarioId")).isEqualTo(uuid(primeiro, "$.prontuarioId"));
		assertThat(uuid(segundo, "$.atendimentoId")).isNotEqualTo(uuid(primeiro, "$.atendimentoId"));

		Paciente paciente = pacienteRepository.findByCpf(cpf).orElseThrow();
		assertThat(paciente.getNome()).isEqualTo("Maria Fictícia");
		assertThat(paciente.getDataNascimento()).isEqualTo(LocalDate.of(1980, 5, 17));
		assertThat(pacienteRepository.findAll().stream().filter(p -> p.getCpf().equals(cpf))).hasSize(1);

		List<Consentimento> consentimentos = consentimentoRepository.historico(pacienteId);
		assertThat(consentimentos).extracting(Consentimento::getDecisao)
				.containsExactly(DecisaoConsentimento.AUTORIZADO);

		UUID segundoAtendimentoId = uuid(segundo, "$.atendimentoId");
		UUID prontuarioId = uuid(primeiro, "$.prontuarioId");
		transacao.executeWithoutResult(s -> {
			Atendimento segundoAtendimento = atendimentoRepository.findById(segundoAtendimentoId).orElseThrow();
			assertThat(segundoAtendimento.getInstituicao().getId()).isEqualTo(hospitalB.getId());
			assertThat(segundoAtendimento.getProntuario().getId()).isEqualTo(prontuarioId);
		});
	}

	@Test
	void semManifestacaoDeConsentimentoOAtendimentoAconteceESemRegistroDeConsentimento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		String corpo = """
				{"cpf":"%s","nome":"Paciente Inconsciente","dataNascimento":"1970-01-01",
				 "instituicaoId":"%s","motivo":"Trauma"}
				""".formatted(Cenario.novoCpf(), hospital.getId());

		MvcResult resultado = mvc.perform(admissao(admin, corpo)).andExpect(status().isCreated()).andReturn();

		assertThat(consentimentoRepository.historico(uuid(resultado, "$.pacienteId"))).isEmpty();
		assertThat(atendimentoRepository.existsById(uuid(resultado, "$.atendimentoId"))).isTrue();
	}

	@Test
	void consentimentoNegadoNaoImpedeOAtendimento() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);

		MvcResult resultado = mvc.perform(admissao(admin, novoCadastro(Cenario.novoCpf(), hospital.getId(), "NEGADO")))
				.andExpect(status().isCreated()).andReturn();

		assertThat(consentimentoRepository.historico(uuid(resultado, "$.pacienteId")))
				.extracting(Consentimento::getDecisao).containsExactly(DecisaoConsentimento.NEGADO);
		assertThat(atendimentoRepository.existsById(uuid(resultado, "$.atendimentoId"))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"52998224724", "11111111111", "5299822472", "529.982.247/25", "abcdefghijk"})
	void cpfInvalidoRetorna400SemGravarNadaESemEcoarOValor(String cpfInvalido) throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		long pacientesAntes = pacienteRepository.count();
		long atendimentosAntes = atendimentoRepository.count();

		MvcResult resultado = mvc.perform(admissao(admin, novoCadastro(cpfInvalido, hospital.getId(), "AUTORIZADO")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.cpf").value("CPF inválido"))
				.andReturn();

		assertThat(resultado.getResponse().getContentAsString()).doesNotContain(cpfInvalido);
		assertThat(pacienteRepository.count()).isEqualTo(pacientesAntes);
		assertThat(atendimentoRepository.count()).isEqualTo(atendimentosAntes);
	}

	@Test
	void primeiroCadastroSemNomeEDataDeNascimentoRetorna400ComOsCampos() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		String cpf = Cenario.novoCpf();
		String corpo = """
				{"cpf":"%s","nome":"   ","instituicaoId":"%s","motivo":"Febre"}
				""".formatted(cpf, hospital.getId());

		mvc.perform(admissao(admin, corpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nome").exists())
				.andExpect(jsonPath("$.campos.dataNascimento").exists());

		assertThat(pacienteRepository.findByCpf(cpf)).isEmpty();
	}

	@Test
	void camposInvalidosRetornam400() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		String corpo = """
				{"cpf":"%s","nome":"%s","dataNascimento":"2999-01-01","instituicaoId":"%s","motivo":" "}
				""".formatted(Cenario.novoCpf(), "x".repeat(151), hospital.getId());

		mvc.perform(admissao(admin, corpo))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nome").exists())
				.andExpect(jsonPath("$.campos.dataNascimento").exists())
				.andExpect(jsonPath("$.campos.motivo").exists());
	}

	@Test
	void dataDeNascimentoNoFuturoUsaOFusoDaAplicacao() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);
		relogio.definir(LocalDateTime.of(2026, 11, 10, 22, 0).atZone(RelogioDeTeste.FUSO).toInstant());
		String corpo = """
				{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"%s","instituicaoId":"%s","motivo":"Febre"}
				""";

		mvc.perform(admissao(admin, corpo.formatted(Cenario.novoCpf(), "2026-11-11", hospital.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.dataNascimento").exists());
		mvc.perform(admissao(admin, corpo.formatted(Cenario.novoCpf(), "2026-11-10", hospital.getId())))
				.andExpect(status().isCreated());
	}

	@Test
	void manifestacaoForaDasOpcoesOuJsonMalformadoRetorna400() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String admin = cenario.recepcao(hospital);

		mvc.perform(admissao(admin, novoCadastro(Cenario.novoCpf(), hospital.getId(), "REVOGADO")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Corpo da requisição inválido."));
		mvc.perform(admissao(admin, "{\"cpf\":")).andExpect(status().isBadRequest());
	}

	@Test
	void recepcaoDeOutraInstituicaoRecebe403ENadaECriado() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		String adminA = cenario.recepcao(hospitalA);
		String cpf = Cenario.novoCpf();

		mvc.perform(admissao(adminA, novoCadastro(cpf, hospitalB.getId(), "AUTORIZADO")))
				.andExpect(status().isForbidden());
		mvc.perform(admissao(adminA, novoCadastro(cpf, UUID.randomUUID(), "AUTORIZADO")))
				.andExpect(status().isForbidden());

		assertThat(pacienteRepository.findByCpf(cpf)).isEmpty();
	}

	@Test
	void vinculoEncerradoOuAindaNaoIniciadoRecebe403() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		LocalDateTime agora = LocalDateTime.ofInstant(INICIO, RelogioDeTeste.FUSO);
		String encerrado = cenario.vinculo(hospital, Perfil.RECEPCAO, agora.minusDays(10), agora.minusDays(1));
		String futuro = cenario.vinculo(hospital, Perfil.RECEPCAO, agora.plusDays(1), null);
		String outroPerfilNoVinculo = cenario.vinculo(hospital, Perfil.MEDICO, agora.minusDays(10), null);

		for (String login : List.of(encerrado, futuro, outroPerfilNoVinculo)) {
			mvc.perform(admissao(login, novoCadastro(Cenario.novoCpf(), hospital.getId(), "AUTORIZADO")))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void perfilQueNaoERecepcaoRecebe403() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		String corpo = novoCadastro(Cenario.novoCpf(), hospital.getId(), "AUTORIZADO");

		String buscaValida = "{\"cpf\":\"%s\",\"instituicaoId\":\"%s\"}".formatted(Cenario.novoCpf(), hospital.getId());

		for (String papel : List.of("ADMINISTRATIVO", "MEDICO", "ENFERMEIRO", "PACIENTE", "AUDITOR_GESTOR")) {
			mvc.perform(post("/api/pacientes/admissoes").with(user("qualquer").roles(papel)).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content(corpo))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/pacientes/busca").with(user("qualquer").roles(papel)).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content(buscaValida))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void semAutenticacaoRecebe401() throws Exception {
		mvc.perform(post("/api/pacientes/admissoes").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/pacientes/busca").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void instituicaoNaoCredenciadaRecebe422() throws Exception {
		Instituicao naoCredenciada = cenario.instituicao(false);
		String admin = cenario.recepcao(naoCredenciada);
		String cpf = Cenario.novoCpf();

		mvc.perform(admissao(admin, novoCadastro(cpf, naoCredenciada.getId(), "AUTORIZADO")))
				.andExpect(status().isUnprocessableContent());
		mvc.perform(busca(admin, cpf, naoCredenciada.getId())).andExpect(status().isUnprocessableContent());

		assertThat(pacienteRepository.findByCpf(cpf)).isEmpty();
	}

	@Test
	void buscaPorCpfEncontraNaRedeSemDevolverCpfNemProntuario() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		MvcResult admissao = mvc.perform(admissao(cenario.recepcao(hospitalA),
				novoCadastro(cpf, hospitalA.getId(), "AUTORIZADO"))).andReturn();

		MvcResult resultado = mvc.perform(busca(cenario.recepcao(hospitalB), Cenario.comMascara(cpf),
						hospitalB.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(uuid(admissao, "$.pacienteId").toString()))
				.andExpect(jsonPath("$.nome").value("Maria Fictícia"))
				.andExpect(jsonPath("$.dataNascimento").value("1980-05-17"))
				.andExpect(jsonPath("$.prontuarioId").doesNotExist())
				.andReturn();

		assertThat(resultado.getResponse().getContentAsString()).doesNotContain(cpf);
	}

	@Test
	void buscaDeCpfInexistenteRetorna404() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		mvc.perform(busca(cenario.recepcao(hospital), Cenario.novoCpf(), hospital.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void buscaSemVinculoComAInstituicaoRecebe403MesmoQueOPacienteExista() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		String cpf = Cenario.novoCpf();
		String adminA = cenario.recepcao(hospitalA);
		mvc.perform(admissao(adminA, novoCadastro(cpf, hospitalA.getId(), "AUTORIZADO")))
				.andExpect(status().isCreated());

		mvc.perform(busca(adminA, cpf, hospitalB.getId()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.nome").doesNotExist());
	}

	@Test
	void cadastrosSimultaneosDoMesmoCpfNaoDuplicamEVinculamAoMesmoProntuario() throws Exception {
		Instituicao hospitalA = cenario.instituicao(true);
		Instituicao hospitalB = cenario.instituicao(true);
		String adminA = cenario.recepcao(hospitalA);
		String adminB = cenario.recepcao(hospitalB);
		String cpf = Cenario.novoCpf();
		CountDownLatch largada = new CountDownLatch(1);

		List<Callable<MvcResult>> tarefas = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String admin = i % 2 == 0 ? adminA : adminB;
			UUID instituicao = i % 2 == 0 ? hospitalA.getId() : hospitalB.getId();
			tarefas.add(() -> {
				largada.await();
				return mvc.perform(admissao(admin, novoCadastro(cpf, instituicao, "AUTORIZADO"))).andReturn();
			});
		}
		List<UUID> prontuarios = new ArrayList<>();
		ExecutorService executor = Executors.newFixedThreadPool(tarefas.size());
		try {
			List<Future<MvcResult>> futuros = tarefas.stream().map(executor::submit).toList();
			largada.countDown();
			for (Future<MvcResult> futuro : futuros) {
				MvcResult resultado = futuro.get(60, TimeUnit.SECONDS);
				assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
				prontuarios.add(uuid(resultado, "$.prontuarioId"));
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(pacienteRepository.findAll().stream().filter(p -> p.getCpf().equals(cpf))).hasSize(1);
		UUID pacienteId = pacienteRepository.findByCpf(cpf).orElseThrow().getId();
		UUID prontuarioId = prontuarioRepository.findByPacienteId(pacienteId).orElseThrow().getId();
		assertThat(prontuarios).hasSize(4).containsOnly(prontuarioId);
		assertThat(consentimentoRepository.historico(pacienteId)).hasSize(1);
	}

	private MockHttpServletRequestBuilder admissao(String login, String corpo) {
		return post("/api/pacientes/admissoes").with(user(login).roles("RECEPCAO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo);
	}

	private MockHttpServletRequestBuilder busca(String login, String cpf, UUID instituicaoId) {
		return post("/api/pacientes/busca").with(user(login).roles("RECEPCAO")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cpf\":\"%s\",\"instituicaoId\":\"%s\"}".formatted(cpf, instituicaoId));
	}

	static String novoCadastro(String cpf, UUID instituicaoId, String consentimento) {
		return """
				{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17","consentimentoInicial":"%s",
				 "instituicaoId":"%s","motivo":"  Dor no peito  "}
				""".formatted(cpf, consentimento, instituicaoId);
	}

	static UUID uuid(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
