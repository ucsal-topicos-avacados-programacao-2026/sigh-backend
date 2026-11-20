package com.sigh.sigh.project.config;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class SegundoFatorConfig {

	@Bean
	public SecretGenerator geradorDeSegredo() {
		return new DefaultSecretGenerator();
	}

	@Bean
	public CodeVerifier verificadorDeCodigo(Clock clock) {
		return new DefaultCodeVerifier(new DefaultCodeGenerator(), () -> clock.instant().getEpochSecond());
	}
}
