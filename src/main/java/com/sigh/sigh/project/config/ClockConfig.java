package com.sigh.sigh.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/**
	 * Relógio único da aplicação, com fuso explícito: toda data e hora gravada ou comparada vem
	 * dele, sem depender do fuso do servidor. Nos testes é substituído por um relógio controlado.
	 */
	@Bean
	Clock clock(@Value("${sigh.fuso-horario}") String fusoHorario) {
		return Clock.system(ZoneId.of(fusoHorario));
	}

	/** Validações como @PastOrPresent usam o mesmo relógio, e não o fuso do servidor. */
	@Bean
	ValidationConfigurationCustomizer relogioDasValidacoes(Clock clock) {
		return configuracao -> configuracao.clockProvider(() -> clock);
	}
}
