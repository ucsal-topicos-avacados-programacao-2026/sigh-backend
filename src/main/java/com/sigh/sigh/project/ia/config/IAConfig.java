package com.sigh.sigh.project.ia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class IAConfig {

	@Bean
	public RestClient clienteHttpIA(IAProperties iaProperties) {
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
}
