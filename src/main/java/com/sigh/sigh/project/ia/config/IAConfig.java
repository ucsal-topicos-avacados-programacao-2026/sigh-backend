package com.sigh.sigh.project.ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class IAConfig {

	@Bean
	public RestClient clienteHttpIA(IAProperties iaProperties,
			@Value("${sigh.ia.exigir-https:false}") boolean exigirHttps) {
		validarTransporte(iaProperties.url(), exigirHttps);
		HttpClient conexao = HttpClient.newBuilder().connectTimeout(iaProperties.tempoLimite()).build();
		JdkClientHttpRequestFactory fabricaDeRequisicoes = new JdkClientHttpRequestFactory(conexao);
		fabricaDeRequisicoes.setReadTimeout(iaProperties.tempoLimite());
		RestClient.Builder construtor = RestClient.builder()
				.baseUrl(iaProperties.url())
				.requestFactory(fabricaDeRequisicoes);
		if (StringUtils.hasText(iaProperties.chave())) {
			construtor.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + iaProperties.chave());
		}
		return construtor.build();
	}

	/**
	 * O serviço de IA recebe dado clínico em claro (é um ponto de decifragem, RNF-004). Com
	 * {@code sigh.ia.exigir-https} (ligado no perfil tls), só aceita HTTPS, salvo serviço na própria máquina.
	 */
	static void validarTransporte(String url, boolean exigirHttps) {
		URI destino = URI.create(url);
		boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(String.valueOf(destino.getHost()));
		if (exigirHttps && !"https".equalsIgnoreCase(destino.getScheme()) && !local) {
			throw new IllegalStateException("sigh.ia.url precisa usar HTTPS: o serviço de IA recebe dados clínicos.");
		}
	}
}
