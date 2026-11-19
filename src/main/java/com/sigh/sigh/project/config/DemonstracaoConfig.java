package com.sigh.sigh.project.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("demonstracao")
public class DemonstracaoConfig {

	@Bean
	public FlywayMigrationStrategy recriarBancoDeDemonstracao() {
		return flyway -> {
			flyway.clean();
			flyway.migrate();
		};
	}
}
