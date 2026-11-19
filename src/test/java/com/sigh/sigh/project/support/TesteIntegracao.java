package com.sigh.sigh.project.support;

import com.sigh.sigh.project.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

/** Base dos testes de integração: PostgreSQL real (Testcontainers), MockMvc e relógio controlado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TesteIntegracao.Relogio.class, Cenario.class})
public abstract class TesteIntegracao {

	protected static final Instant INICIO = Instant.parse("2026-11-10T12:00:00Z");

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected RelogioDeTeste relogio;

	@Autowired
	protected Cenario cenario;

	@BeforeEach
	void reiniciarRelogio() {
		relogio.definir(INICIO);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Relogio {

		@Bean
		@Primary
		RelogioDeTeste relogioDeTeste() {
			return new RelogioDeTeste(INICIO);
		}
	}
}
