package com.sigh.sigh.project.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rotas -> rotas
						.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
						.requestMatchers("/auth/segundo-fator/**").hasAuthority(TokenService.SEGUNDO_FATOR_PENDENTE)
						.anyRequest().hasAnyRole(nomesDosPerfis()))
				.oauth2ResourceServer(servidor -> servidor.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtEncoder codificadorDeToken(JwtProperties jwtProperties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtProperties.chave()));
	}

	@Bean
	JwtDecoder decodificadorDeToken(JwtProperties jwtProperties) {
		return NimbusJwtDecoder.withSecretKey(jwtProperties.chave()).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtAuthenticationConverter conversorDeToken() {
		JwtGrantedAuthoritiesConverter autorizacoes = new JwtGrantedAuthoritiesConverter();
		autorizacoes.setAuthoritiesClaimName(TokenService.CLAIM_AUTORIZACOES);
		autorizacoes.setAuthorityPrefix("");
		JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
		conversor.setJwtGrantedAuthoritiesConverter(autorizacoes);
		return conversor;
	}

	private static String[] nomesDosPerfis() {
		return Arrays.stream(Perfil.values()).map(Perfil::name).toArray(String[]::new);
	}
}
