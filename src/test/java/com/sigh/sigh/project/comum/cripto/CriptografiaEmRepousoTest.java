package com.sigh.sigh.project.comum.cripto;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.support.Cenario;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério de aceite do BE-09: o dado clínico não aparece em texto claro no banco (e, por isso, nem
 * nas cópias), e a aplicação continua lendo o texto claro (RNF-004).
 */
class CriptografiaEmRepousoTest extends TesteIntegracao {

	private static final String TRIAGEM = """
			{"atendimentoId":"%s","sintomas":"Dor torácica irradiada","sinaisVitais":{"pressaoArterial":"150/95",
			 "frequenciaCardiaca":112,"frequenciaRespiratoria":24,"temperatura":37.8,"saturacaoOxigenio":93.5}}
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	CifragemDeDadosLegados cifragemDeDadosLegados;

	@Autowired
	ResumoHistoricoService resumoHistoricoService;

	@Autowired
	PostgreSQLContainer banco;

	@Test
	void dadosClinicosFicamCifradosNoBancoELegiveisNaAplicacao() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		MvcResult admissao = admitir(hospital);
		UUID pacienteId = id(admissao, "$.pacienteId");
		UUID atendimentoId = id(admissao, "$.atendimentoId");
		String medico = cenario.profissional(hospital, Perfil.MEDICO);
		String enfermeiro = cenario.profissional(hospital, Perfil.ENFERMEIRO);

		String triagem = mvc.perform(post("/triagens").with(user(enfermeiro).roles("ENFERMEIRO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(TRIAGEM.formatted(atendimentoId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sintomas").value("Dor torácica irradiada"))
				.andExpect(jsonPath("$.sinaisVitais.frequenciaCardiaca").value(112))
				.andExpect(jsonPath("$.sinaisVitais.temperatura").value(37.8))
				.andReturn().getResponse().getContentAsString();
		// O médico registra depois da triagem: com o atendimento em curso, não se tria mais.
		mvc.perform(post("/atendimentos/{id}/registros", atendimentoId).with(user(medico).roles("MEDICO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"tipo\":\"DIAGNOSTICO\",\"conteudo\":\"Infarto agudo do miocárdio\",\"categoriaInformacao\":\"Cardiologia\",\"codigoCID10\":\"I21.9\"}"))
				.andExpect(status().isCreated());
		UUID resumoId = resumoHistoricoService.solicitar(atendimentoId, medico).resumoId();
		resumoHistoricoService.concluir(resumoId, "Infarto agudo do miocárdio recente.");
		mvc.perform(post("/atendimentos/{id}/historico/emergencia", atendimentoId).with(user(medico).roles("MEDICO"))
						.with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"justificativa\":\"Parada cardiorrespiratória em andamento na sala vermelha\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registros[*].conteudo").value(hasItem("Infarto agudo do miocárdio")))
				.andExpect(jsonPath("$.registros[*].codigoCID10").value(hasItem("I21.9")));

		List<String> gravados = jdbc.queryForList("""
				select conteudo from registro_clinico where prontuario_id = (select id from prontuario where paciente_id = ?)
				union all select codigo_cid10 from registro_clinico where prontuario_id = (select id from prontuario where paciente_id = ?)
				union all select motivo from atendimento where id = ?
				union all select sintomas from triagem where id = ?
				union all select pressao_arterial from triagem where id = ?
				union all select frequencia_cardiaca from triagem where id = ?
				union all select frequencia_respiratoria from triagem where id = ?
				union all select temperatura from triagem where id = ?
				union all select saturacao_oxigenio from triagem where id = ?
				union all select texto from resumo_historico_ia where id = ?
				union all select justificativa from registro_auditoria where emergencia and prontuario_id =
				  (select id from prontuario where paciente_id = ?)
				""", String.class, pacienteId, pacienteId, atendimentoId, UUID.fromString(JsonPath.read(triagem, "$.id")),
				UUID.fromString(JsonPath.read(triagem, "$.id")), UUID.fromString(JsonPath.read(triagem, "$.id")),
				UUID.fromString(JsonPath.read(triagem, "$.id")), UUID.fromString(JsonPath.read(triagem, "$.id")),
				UUID.fromString(JsonPath.read(triagem, "$.id")), resumoId, pacienteId);

		assertThat(gravados).hasSize(11).allSatisfy(valor -> assertThat(valor).startsWith("sigh:v1:"));
		// Só trechos que não podem surgir por acaso no Base64 (acento, ponto, espaço ou palavra longa).
		assertThat(String.join(" ", gravados)).doesNotContain("Infarto", "I21.9", "torácica", "150/95", "37.8", "93.5",
				"Parada", "Febre alta");
	}

	/**
	 * O valor legado é cifrado e a versão antiga da linha, em claro, some também dos arquivos do banco
	 * (VACUUM FULL), que é o que o backup físico copia.
	 */
	@Test
	void valorGravadoEmClaroAntesDaCriptografiaEhCifradoNaInicializacao() throws Exception {
		Instituicao hospital = cenario.instituicao(true);
		UUID pacienteId = id(admitir(hospital), "$.pacienteId");
		UUID registro = cenario.registroClinico(pacienteId, hospital, "Clínica geral", "Asma");
		String marcador = "AsmaLegadaEmClaro" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("update registro_clinico set conteudo = ?, codigo_cid10 = 'J45' where id = ?", marcador, registro);
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).as("controle: o valor legado está nos arquivos").isTrue();

		assertThat(cifragemDeDadosLegados.cifrarLegados()).isGreaterThanOrEqualTo(2);
		jdbc.execute("checkpoint");
		assertThat(nosArquivosDoBanco(marcador)).as("nem a versão antiga da linha sobra em claro").isFalse();

		String conteudo = jdbc.queryForObject("select conteudo from registro_clinico where id = ?", String.class, registro);
		assertThat(conteudo).startsWith("sigh:v1:").doesNotContain("Asma");
		assertThat(cifragemDeDadosLegados.cifrarLegados()).isZero();
		String titular = cenario.contaDoPaciente(pacienteId);
		mvc.perform(get("/api/pacientes/{id}/historico", pacienteId).with(user(titular).roles("PACIENTE")))
				.andExpect(jsonPath("$[0].conteudo").value(marcador))
				.andExpect(jsonPath("$[0].codigoCID10").value("J45"));
	}

	/**
	 * A lista que a cifragem de legado percorre bate com os campos marcados com conversor de cifra nas
	 * entidades: um campo novo cifrado sem entrar na lista faria este teste falhar. A justificativa da
	 * auditoria fica de fora de propósito (tabela imutável).
	 */
	@Test
	void listaDaCifragemDeLegadoBateComOsCamposCifradosDasEntidades() throws Exception {
		Set<String> anotadas = new TreeSet<>();
		ClassPathScanningCandidateComponentProvider busca = new ClassPathScanningCandidateComponentProvider(false);
		busca.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
		for (BeanDefinition candidata : busca.findCandidateComponents("com.sigh.sigh.project")) {
			Class<?> entidade = Class.forName(candidata.getBeanClassName());
			String tabela = entidade.getAnnotation(Table.class).name();
			for (Field campo : entidade.getDeclaredFields()) {
				if (campo.getType().isAnnotationPresent(Embeddable.class)) {
					for (Field embutido : campo.getType().getDeclaredFields()) {
						colunaCifrada(tabela, embutido).ifPresent(anotadas::add);
					}
				}
				colunaCifrada(tabela, campo).ifPresent(anotadas::add);
			}
		}
		Set<String> naLista = new TreeSet<>();
		CifragemDeDadosLegados.COLUNAS.forEach(coluna -> naLista.add(coluna[0] + "." + coluna[1]));
		naLista.add("registro_auditoria.justificativa");

		assertThat(anotadas).isEqualTo(naLista);
	}

	private static Optional<String> colunaCifrada(String tabela, Field campo) {
		Convert conversor = campo.getAnnotation(Convert.class);
		if (conversor == null || !Set.of(TextoCifrado.class, InteiroCifrado.class, DecimalCifrado.class)
				.contains(conversor.converter())) {
			return Optional.empty();
		}
		Column coluna = campo.getAnnotation(Column.class);
		String nome = coluna != null && !coluna.name().isEmpty() ? coluna.name()
				: campo.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
		return Optional.of(tabela + "." + nome);
	}

	private boolean nosArquivosDoBanco(String texto) throws Exception {
		ExecResult busca = banco.execInContainer("sh", "-c",
				"LC_ALL=C grep -r -a -l -F '" + texto + "' /var/lib/postgresql/data/base");
		assertThat(busca.getExitCode()).as(busca.getStderr()).isIn(0, 1);
		return busca.getExitCode() == 0;
	}

	@Test
	void nenhumaColunaClinicaFicaEmClaroDepoisDaInicializacao() {
		cifragemDeDadosLegados.cifrarLegados();
		for (String[] coluna : CifragemDeDadosLegados.COLUNAS) {
			Integer emClaro = jdbc.queryForObject("select count(*) from " + coluna[0] + " where " + coluna[1]
					+ " is not null and " + coluna[1] + " not like 'sigh:v1:%'", Integer.class);
			assertThat(emClaro).as(coluna[0] + "." + coluna[1]).isZero();
		}
	}

	private MvcResult admitir(Instituicao hospital) throws Exception {
		return mvc.perform(post("/api/pacientes/admissoes")
						.with(user(cenario.administrativo(hospital)).roles("ADMINISTRATIVO")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"cpf":"%s","nome":"Maria Fictícia","dataNascimento":"1980-05-17",
								 "consentimentoInicial":"AUTORIZADO","instituicaoId":"%s","motivo":"Febre alta"}
								""".formatted(Cenario.novoCpf(), hospital.getId())))
				.andExpect(status().isCreated()).andReturn();
	}

	private static UUID id(MvcResult resultado, String caminho) throws Exception {
		return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), caminho));
	}
}
