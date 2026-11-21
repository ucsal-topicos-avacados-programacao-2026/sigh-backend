package com.sigh.sigh.project.triagem.config;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("sigh.triagem.tempo-maximo-espera")
public record TempoMaximoEsperaProperties(
		@NotNull Duration vermelho,
		@NotNull Duration laranja,
		@NotNull Duration amarelo,
		@NotNull Duration verde,
		@NotNull Duration azul) {

	public Duration da(CorRisco cor) {
		return switch (cor) {
			case VERMELHO -> vermelho;
			case LARANJA -> laranja;
			case AMARELO -> amarelo;
			case VERDE -> verde;
			case AZUL -> azul;
		};
	}
}
