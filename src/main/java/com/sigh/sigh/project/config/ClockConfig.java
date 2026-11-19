package com.sigh.sigh.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/**
	 * Relógio único da aplicação, com fuso explícito: o horário gravado não depende do fuso
	 * do servidor. Nos testes é substituído por um relógio controlado.
	 */
	@Bean
	Clock clock(@Value("${sigh.fuso-horario}") String fusoHorario) {
		return Clock.system(ZoneId.of(fusoHorario));
	}
}
