package com.sigh.sigh.project.config;

import com.sigh.sigh.project.support.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-02: o front-end de outra origem só chama a API se a origem estiver em sigh.cors.origens-permitidas. */
@TestPropertySource(properties = "sigh.cors.origens-permitidas=http://localhost:5173, https://sigh.local")
class CorsTest extends TesteIntegracao {

	@Test
	void origemPermitidaPassaNaPreverificacaoComOTokenNoCabecalho() throws Exception {
		mvc.perform(options("/usuarios/eu")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET")
						.header("Access-Control-Request-Headers", "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		mvc.perform(options("/auth/login")
						.header("Origin", "https://sigh.local")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://sigh.local"));
	}

	@Test
	void patchDaTelaDeLeitosPassaNaPreverificacao() throws Exception {
		mvc.perform(options("/leitos/{id}/status", java.util.UUID.randomUUID())
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "PATCH")
						.header("Access-Control-Request-Headers", "authorization, content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
	}

	/** Sessão expirada: o 401 chega ao front com o cabeçalho de CORS, senão o navegador mostraria só erro de rede. */
	@Test
	void respostaSemTokenChegaAoFrontComOCabecalhoDeCors() throws Exception {
		mvc.perform(get("/usuarios/eu").header("Origin", "http://localhost:5173"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void outraOrigemEhRecusada() throws Exception {
		mvc.perform(options("/usuarios/eu")
						.header("Origin", "https://site-qualquer.example")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mvc.perform(get("/usuarios/eu").header("Origin", "https://site-qualquer.example"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void semAPropriedadeNaoHaCorsENaoSeAceitaQualquerOrigem() {
		SecurityConfig config = new SecurityConfig();
		org.springframework.mock.web.MockHttpServletRequest pedido = new org.springframework.mock.web.MockHttpServletRequest(
				"GET", "/usuarios/eu");
		org.assertj.core.api.Assertions.assertThat(config.corsConfigurationSource("").getCorsConfiguration(pedido))
				.as("sem origens, nenhuma configuração de CORS").isNull();
		assertThatThrownBy(() -> config.corsConfigurationSource("http://localhost:5173,*"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("*");
	}
}
