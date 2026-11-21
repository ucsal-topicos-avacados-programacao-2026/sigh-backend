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
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class IAConfig {

	private static final Set<String> NA_PROPRIA_MAQUINA = Set.of("localhost", "127.0.0.1", "[::1]");

	@Bean
	public RestClient clienteHttpIA(IAProperties iaProperties,
			@Value("${sigh.ia.restringir-destino:false}") boolean restringirDestino,
			@Value("${sigh.ia.hosts-autorizados:}") String hostsAutorizados) {
		validarDestino(iaProperties.url(), restringirDestino, hostsAutorizados);
		return construir(iaProperties, iaProperties.tempoLimite());
	}

	@Bean
	public RestClient clienteHttpIAResumo(IAProperties iaProperties,
			@Value("${sigh.ia.restringir-destino:false}") boolean restringirDestino,
			@Value("${sigh.ia.hosts-autorizados:}") String hostsAutorizados) {
		validarDestino(iaProperties.url(), restringirDestino, hostsAutorizados);
		return construir(iaProperties, iaProperties.tempoLimiteResumo());
	}

	private static RestClient construir(IAProperties iaProperties, Duration tempoLimite) {
		HttpClient conexao = HttpClient.newBuilder()
				.connectTimeout(iaProperties.tempoLimite())
				.followRedirects(HttpClient.Redirect.NEVER)
				.proxy(HttpClient.Builder.NO_PROXY)
				.build();
		JdkClientHttpRequestFactory fabricaDeRequisicoes = new JdkClientHttpRequestFactory(conexao);
		fabricaDeRequisicoes.setReadTimeout(tempoLimite);
		RestClient.Builder construtor = RestClient.builder()
				.baseUrl(iaProperties.url())
				.requestFactory(fabricaDeRequisicoes);
		if (StringUtils.hasText(iaProperties.chave())) {
			construtor.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + iaProperties.chave());
		}
		return construtor.build();
	}

	static void validarDestino(String url, boolean restringirDestino, String hostsAutorizados) {
		if (!restringirDestino) {
			return;
		}
		URI destino = URI.create(url);
		String esquema = String.valueOf(destino.getScheme()).toLowerCase(Locale.ROOT);
		if (!esquema.equals("http") && !esquema.equals("https")) {
			throw new IllegalStateException("sigh.ia.url precisa ser um endereço http ou https.");
		}
		String host = destino.getHost() == null ? "" : destino.getHost().toLowerCase(Locale.ROOT);
		if (NA_PROPRIA_MAQUINA.contains(host)) {
			return;
		}
		if (!esquema.equals("https")) {
			throw new IllegalStateException("sigh.ia.url precisa usar HTTPS: o serviço de IA recebe dados clínicos.");
		}
		if (!hosts(hostsAutorizados).contains(host)) {
			throw new IllegalStateException("O host de sigh.ia.url não está em sigh.ia.hosts-autorizados: o serviço "
					+ "de IA recebe dados clínicos e só pode ser um componente autorizado no C4.");
		}
	}

	private static Set<String> hosts(String lista) {
		return Arrays.stream(lista.split(","))
				.map(String::strip)
				.filter(host -> !host.isEmpty())
				.map(host -> host.toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
	}
}
