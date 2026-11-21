package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteIATest {

	private static final String ENDERECO_IA = "http://ia.triagem";
	private static final String ENDERECO_IA_RESUMO = "http://ia.resumo";
	private static final IAProperties CONFIGURACAO = new IAProperties(
			ENDERECO_IA, "modelo-teste", null, 0, Duration.ofSeconds(5), 20,
			new ByteArrayResource("Instruções de triagem".getBytes(StandardCharsets.UTF_8)),
			new ByteArrayResource("Instruções de resumo".getBytes(StandardCharsets.UTF_8)),
			Duration.ofSeconds(10), 5, false);

	private MockRestServiceServer servidorIA;
	private ClienteIA clienteIA;

	@BeforeEach
	public void prepararServidorSimulado() throws Exception {
		RestClient.Builder construtor = RestClient.builder();
		servidorIA = MockRestServiceServer.bindTo(construtor).build();
		RestClient clienteTriagem = construtor.baseUrl(ENDERECO_IA).build();
		RestClient clienteResumo = construtor.baseUrl(ENDERECO_IA_RESUMO).build();
		clienteIA = new ClienteIA(clienteTriagem, clienteResumo, CONFIGURACAO, JsonMapper.builder().build());
	}

	@Test
	void enviaOPedidoNaConfiguracaoDoEstudoEDevolveACor() {
		servidorIA.expect(requestTo(ENDERECO_IA + "/v1/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.model").value("modelo-teste"))
				.andExpect(jsonPath("$.temperature").value(0.0))
				.andExpect(jsonPath("$.reasoning_effort").value("none"))
				.andExpect(jsonPath("$.response_format.json_schema.schema.properties.cor.enum.length()").value(5))
				.andExpect(jsonPath("$.messages[1].content").value("Sintomas: dor torácica"))
				.andRespond(withSuccess(respostaComConteudo("{\\\"cor\\\": \\\"LARANJA\\\"}"), MediaType.APPLICATION_JSON));

		CorRisco cor = clienteIA.classificarRisco("Sintomas: dor torácica");

		assertThat(cor).isEqualTo(CorRisco.LARANJA);
		servidorIA.verify();
	}

	@Test
	void corForaDoProtocoloEhRecusada() {
		servidorIA.expect(requestTo(ENDERECO_IA + "/v1/chat/completions"))
				.andRespond(withSuccess(respostaComConteudo("{\\\"cor\\\": \\\"ROXO\\\"}"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> clienteIA.classificarRisco("Sintomas: tosse"))
				.isInstanceOf(IAIndisponivelException.class);
	}

	@Test
	void respostaQueNaoEhJsonEhRecusada() {
		servidorIA.expect(requestTo(ENDERECO_IA + "/v1/chat/completions"))
				.andRespond(withSuccess(respostaComConteudo("Acho que é amarelo"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> clienteIA.classificarRisco("Sintomas: tosse"))
				.isInstanceOf(IAIndisponivelException.class);
	}

	@Test
	void falhaDoServicoViraAvisoParaClassificacaoManual() {
		servidorIA.expect(requestTo(ENDERECO_IA + "/v1/chat/completions")).andRespond(withServerError());

		assertThatThrownBy(() -> clienteIA.classificarRisco("Sintomas: tosse"))
				.isInstanceOf(IAIndisponivelException.class)
				.hasMessageContaining("sem a sugestão da IA");
	}

	@Test
	void resumoEhPedidoEmTextoLivreComAsInstrucoesDeResumo() {
		servidorIA.expect(requestTo(ENDERECO_IA_RESUMO + "/v1/chat/completions"))
				.andExpect(jsonPath("$.messages[0].content").value("Instruções de resumo"))
				.andExpect(jsonPath("$.messages[1].content").value("- 2026-09-20 | Hospital | ALERGIA: dipirona"))
				.andExpect(jsonPath("$.response_format").doesNotExist())
				.andRespond(withSuccess(respostaComConteudo("  Alergias: dipirona.  "), MediaType.APPLICATION_JSON));

		String resumo = clienteIA.resumirHistorico("- 2026-09-20 | Hospital | ALERGIA: dipirona");

		assertThat(resumo).isEqualTo("Alergias: dipirona.");
	}

	@Test
	void aquecimentoPedeUmaRespostaMinimaPeloClienteDoResumo() {
		servidorIA.expect(requestTo(ENDERECO_IA_RESUMO + "/v1/chat/completions"))
				.andExpect(jsonPath("$.model").value("modelo-teste"))
				.andExpect(jsonPath("$.messages[0].content").value("Responda apenas: ok"))
				.andRespond(withSuccess(respostaComConteudo("ok"), MediaType.APPLICATION_JSON));

		clienteIA.aquecer();

		servidorIA.verify();
	}

	@Test
	void resumoVazioEhRecusado() {
		servidorIA.expect(requestTo(ENDERECO_IA_RESUMO + "/v1/chat/completions"))
				.andRespond(withSuccess(respostaComConteudo(" "), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> clienteIA.resumirHistorico("- registro"))
				.isInstanceOf(IAIndisponivelException.class);
	}

	private String respostaComConteudo(String conteudo) {
		return """
				{"choices": [{"message": {"role": "assistant", "content": "%s"}}]}
				""".formatted(conteudo);
	}
}
