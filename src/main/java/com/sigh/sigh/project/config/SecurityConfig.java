package com.sigh.sigh.project.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rotas -> rotas
						.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers("/auth/segundo-fator/**").hasAuthority(TokenService.SEGUNDO_FATOR_PENDENTE)
						.anyRequest().hasAnyRole(nomesDosPerfis()))
				.oauth2ResourceServer(servidor -> servidor.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/**
	 * CORS para o front-end servido de outra origem (FE-02): só as origens de {@code sigh.cors.origens-permitidas}
	 * (separadas por vírgula), com o token no cabeçalho Authorization, sem cookies. Sem a propriedade, não há
	 * CORS e o navegador recusa chamada de outra origem. "*" não é aceito: a API traz dado clínico.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${sigh.cors.origens-permitidas:}") String origens) {
		List<String> permitidas = Arrays.stream(origens.split(","))
				.map(String::strip)
				.filter(origem -> !origem.isEmpty())
				.toList();
		if (permitidas.contains("*")) {
			throw new IllegalStateException("sigh.cors.origens-permitidas não aceita \"*\": liste as origens do front-end.");
		}
		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		if (!permitidas.isEmpty()) {
			CorsConfiguration cors = new CorsConfiguration();
			cors.setAllowedOrigins(permitidas);
			// Só os métodos que a API tem.
			cors.setAllowedMethods(List.of("GET", "POST", "PATCH"));
			cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
			cors.setAllowCredentials(false);
			cors.setMaxAge(Duration.ofHours(1));
			fonte.registerCorsConfiguration("/**", cors);
		}
		return fonte;
	}

	@Bean
	public PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public JwtEncoder codificadorDeToken(JwtProperties jwtProperties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtProperties.chave()));
	}

	@Bean
	public JwtDecoder decodificadorDeToken(JwtProperties jwtProperties) {
		return NimbusJwtDecoder.withSecretKey(jwtProperties.chave()).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	public JwtAuthenticationConverter conversorDeToken() {
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
