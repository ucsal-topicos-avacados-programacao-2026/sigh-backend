package com.sigh.sigh.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock(@Value("${sigh.fuso-horario}") String fusoHorario) {
		return Clock.system(ZoneId.of(fusoHorario));
	}

	@Bean
	ValidationConfigurationCustomizer relogioDasValidacoes(Clock clock) {
		return configuracao -> configuracao.clockProvider(() -> clock);
	}
}
