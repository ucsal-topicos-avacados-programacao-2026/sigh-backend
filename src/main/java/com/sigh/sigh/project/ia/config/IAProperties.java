package com.sigh.sigh.project.ia.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("sigh.ia")
public record IAProperties(
		@NotBlank String url,
		@NotBlank String modelo,
		String chave,
		@PositiveOrZero double temperatura,
		@NotNull Duration tempoLimite,
		@Positive int limiteRegistrosHistorico,
		@NotNull Resource instrucoesTriagem,
		@NotNull Resource instrucoesResumo) {
}
