package com.sigh.sigh.project.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties("sigh.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32) String segredo,
		@NotNull Duration validade,
		@NotNull Duration validadeSegundoFatorPendente) {

	public SecretKey chave() {
		return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
