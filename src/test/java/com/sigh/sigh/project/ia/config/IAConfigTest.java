package com.sigh.sigh.project.ia.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O serviço de IA recebe dado clínico em claro (ponto de decifragem, RNF-004). No perfil tls, o dado só sai
 * para a própria máquina ou para um host autorizado, por HTTPS, e vai direto: sem redirecionamento e sem proxy.
 */
class IAConfigTest {

	private static final String AUTORIZADOS = "ia.rede-sigh.org, IA.Hospital.org";

	@Test
	void naPropriaMaquinaAceitaHttp() {
		for (String url : new String[] {"http://localhost:11434", "http://127.0.0.1:11434", "http://[::1]:11434",
				"http://LOCALHOST:11434", "https://localhost"}) {
			assertThatCode(() -> IAConfig.validarDestino(url, true, "")).as(url).doesNotThrowAnyException();
		}
	}

	@Test
	void foraDaMaquinaSoHostAutorizadoEPorHttps() {
		assertThatCode(() -> IAConfig.validarDestino("https://ia.rede-sigh.org/api", true, AUTORIZADOS))
				.doesNotThrowAnyException();
		assertThatCode(() -> IAConfig.validarDestino("https://ia.hospital.org", true, AUTORIZADOS))
				.doesNotThrowAnyException();

		assertThatThrownBy(() -> IAConfig.validarDestino("http://ia.rede-sigh.org", true, AUTORIZADOS))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
		assertThatThrownBy(() -> IAConfig.validarDestino("https://ia.externa.com", true, AUTORIZADOS))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("sigh.ia.hosts-autorizados");
		assertThatThrownBy(() -> IAConfig.validarDestino("https://ia.externa.com", true, ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("sigh.ia.hosts-autorizados");
	}

	@Test
	void hostAutorizadoNaoSeForjaPeloUsuarioNemPorSufixo() {
		assertThatThrownBy(() -> IAConfig.validarDestino("https://ia.rede-sigh.org@ia.externa.com", true, AUTORIZADOS))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> IAConfig.validarDestino("https://ia.rede-sigh.org.externa.com", true, AUTORIZADOS))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> IAConfig.validarDestino("https://localhost.externa.com", true, ""))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void recusaEnderecoSemEsquemaWeb() {
		assertThatThrownBy(() -> IAConfig.validarDestino("localhost:11434", true, ""))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> IAConfig.validarDestino("ftp://localhost", true, ""))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void semARestricaoAceitaQualquerDestino() {
		assertThatCode(() -> IAConfig.validarDestino("http://ia.externa.com:11434", false, ""))
				.doesNotThrowAnyException();
	}

	@Test
	void naoSegueRedirecionamentoParaOutroDestino() throws Exception {
		AtomicInteger pedidosNoDesvio = new AtomicInteger();
		HttpServer desvio = servidor();
		desvio.createContext("/", troca -> {
			pedidosNoDesvio.incrementAndGet();
			troca.sendResponseHeaders(200, -1);
			troca.close();
		});
		HttpServer ia = servidor();
		String enderecoDoDesvio = "http://127.0.0.1:" + desvio.getAddress().getPort() + "/v1/chat/completions";
		ia.createContext("/", troca -> {
			troca.getResponseHeaders().add("Location", enderecoDoDesvio);
			troca.sendResponseHeaders(307, -1);
			troca.close();
		});
		desvio.start();
		ia.start();
		try {
			RestClient cliente = new IAConfig().clienteHttpIA(
					configuracao("http://127.0.0.1:" + ia.getAddress().getPort()), true, "");

			int status = cliente.post().uri("/v1/chat/completions").body("{\"conteudo\":\"dado clínico\"}")
					.retrieve().toBodilessEntity().getStatusCode().value();

			assertThat(status).isEqualTo(307);
			assertThat(pedidosNoDesvio).hasValue(0);
		} finally {
			ia.stop(0);
			desvio.stop(0);
		}
	}

	@Test
	void naoPassaPorProxyConfiguradoNaJvm() throws Exception {
		AtomicInteger pedidosNaIA = new AtomicInteger();
		AtomicInteger pedidosNoProxy = new AtomicInteger();
		HttpServer ia = servidor();
		ia.createContext("/", troca -> {
			pedidosNaIA.incrementAndGet();
			troca.sendResponseHeaders(200, -1);
			troca.close();
		});
		HttpServer proxy = servidor();
		proxy.createContext("/", troca -> {
			pedidosNoProxy.incrementAndGet();
			troca.sendResponseHeaders(200, -1);
			troca.close();
		});
		ia.start();
		proxy.start();
		ProxySelector anterior = ProxySelector.getDefault();
		ProxySelector.setDefault(ProxySelector.of(proxy.getAddress()));
		try {
			RestClient cliente = new IAConfig().clienteHttpIA(
					configuracao("http://127.0.0.1:" + ia.getAddress().getPort()), true, "");

			cliente.post().uri("/v1/chat/completions").body("{\"conteudo\":\"dado clínico\"}")
					.retrieve().toBodilessEntity();

			assertThat(pedidosNoProxy).hasValue(0);
			assertThat(pedidosNaIA).hasValue(1);
		} finally {
			ProxySelector.setDefault(anterior);
			proxy.stop(0);
			ia.stop(0);
		}
	}

	/** A sugestão da triagem espera até tempo-limite; o resumo, em segundo plano, até tempo-limite-resumo. */
	@Test
	void clienteDoResumoEsperaMaisQueODaTriagem() throws Exception {
		HttpServer iaLenta = servidor();
		iaLenta.createContext("/", troca -> {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException interrompido) {
				Thread.currentThread().interrupt();
			}
			troca.sendResponseHeaders(200, -1);
			troca.close();
		});
		iaLenta.start();
		try {
			IAProperties configuracao = new IAProperties("http://127.0.0.1:" + iaLenta.getAddress().getPort(),
					"modelo-teste", null, 0, Duration.ofMillis(500), 20,
					new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)),
					new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)), Duration.ofSeconds(5), 5, false);
			IAConfig config = new IAConfig();

			assertThatThrownBy(() -> config.clienteHttpIA(configuracao, true, "").post().uri("/v1/chat/completions")
					.body("{}").retrieve().toBodilessEntity())
					.as("triagem: desiste em 500 ms").isInstanceOf(ResourceAccessException.class);
			assertThat(config.clienteHttpIAResumo(configuracao, true, "").post().uri("/v1/chat/completions")
					.body("{}").retrieve().toBodilessEntity().getStatusCode().value())
					.as("resumo: espera a IA lenta").isEqualTo(200);
		} finally {
			iaLenta.stop(0);
		}
	}

	@Test
	void clienteDoResumoTambemSoVaiAosDestinosAutorizados() {
		assertThatThrownBy(() -> new IAConfig().clienteHttpIAResumo(configuracao("https://ia.externa.com"), true, ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("sigh.ia.hosts-autorizados");
	}

	private static IAProperties configuracao(String url) {
		return new IAProperties(url, "modelo-teste", null, 0, Duration.ofSeconds(5), 20,
				new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)),
				new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)), Duration.ofSeconds(5), 5, false);
	}

	private static HttpServer servidor() throws Exception {
		return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
	}
}
