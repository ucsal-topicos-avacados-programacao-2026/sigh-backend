package com.sigh.sigh.project.ia.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O serviço de IA recebe dado clínico em claro: no perfil tls, só por HTTPS, salvo na própria máquina. */
class IAConfigTest {

	@Test
	void comHttpsExigidoRecusaHttpParaOutraMaquina() {
		assertThatThrownBy(() -> IAConfig.validarTransporte("http://ia.exemplo.org:11434", true))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
		assertThatCode(() -> IAConfig.validarTransporte("https://ia.exemplo.org", true)).doesNotThrowAnyException();
		assertThatCode(() -> IAConfig.validarTransporte("http://localhost:11434", true)).doesNotThrowAnyException();
		assertThatCode(() -> IAConfig.validarTransporte("http://127.0.0.1:11434", true)).doesNotThrowAnyException();
		assertThatCode(() -> IAConfig.validarTransporte("http://[::1]:11434", true)).doesNotThrowAnyException();
	}

	@Test
	void semAExigenciaAceitaHttp() {
		assertThatCode(() -> IAConfig.validarTransporte("http://ia.exemplo.org:11434", false)).doesNotThrowAnyException();
	}
}
