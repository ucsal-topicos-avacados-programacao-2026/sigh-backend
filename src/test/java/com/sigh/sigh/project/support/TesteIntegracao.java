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
import java.time.LocalDateTime;

/** Base dos testes de integração: PostgreSQL real (Testcontainers), MockMvc e relógio controlado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TesteIntegracao.Relogio.class, Cenario.class})
public abstract class TesteIntegracao {

	/** 10/11/2026 às 12:00 no fuso da aplicação (15:00 UTC). */
	protected static final Instant INICIO = LocalDateTime.of(2026, 11, 10, 12, 0).atZone(RelogioDeTeste.FUSO).toInstant();

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
