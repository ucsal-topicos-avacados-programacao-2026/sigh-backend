package com.sigh.sigh.project.config;

import com.sigh.sigh.project.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-09 (RNF-004): no perfil tls o back-end só fala HTTPS, com TLS 1.2 ou 1.3, e manda o navegador
 * usar sempre HTTPS (HSTS). O certificado é fictício, gerado na hora com o keytool do JDK e entregue em
 * PEM, como em produção (SIGH_TLS_CERTIFICADO e SIGH_TLS_CHAVE_PRIVADA).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tls")
@Import(TestcontainersConfiguration.class)
class TlsTest {

	private static final String SENHA = "senha-ficticia-do-teste";
	private static Path keystore;
	private static Path certificadoPem;
	private static Path chavePem;

	@LocalServerPort
	int porta;

	@BeforeAll
	static void gerarCertificado() throws Exception {
		keystore = Files.createTempDirectory("sigh-tls").resolve("servidor.p12");
		Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
				"-genkeypair", "-alias", "sigh", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "2",
				"-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
				"-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", SENHA, "-keypass", SENHA)
				.redirectErrorStream(true).start();
		String saida = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(keytool.waitFor()).as(saida).isZero();

		KeyStore p12 = KeyStore.getInstance("PKCS12");
		try (InputStream entrada = new FileInputStream(keystore.toFile())) {
			p12.load(entrada, SENHA.toCharArray());
		}
		certificadoPem = keystore.resolveSibling("certificado.pem");
		chavePem = keystore.resolveSibling("chave.pem");
		Files.writeString(certificadoPem, pem("CERTIFICATE", p12.getCertificate("sigh").getEncoded()));
		Files.writeString(chavePem, pem("PRIVATE KEY", p12.getKey("sigh", SENHA.toCharArray()).getEncoded()));
	}

	private static String pem(String tipo, byte[] conteudo) {
		return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(conteudo)
				+ "\n-----END " + tipo + "-----\n";
	}

	@DynamicPropertySource
	static void certificado(DynamicPropertyRegistry propriedades) {
		propriedades.add("SIGH_TLS_CERTIFICADO", () -> "file:" + certificadoPem);
		propriedades.add("SIGH_TLS_CHAVE_PRIVADA", () -> "file:" + chavePem);
	}

	@Test
	void respondeSoPorHttpsComHstsETls13() throws Exception {
		HttpClient cliente = HttpClient.newBuilder().sslContext(confiandoNoCertificado()).build();
		HttpResponse<String> resposta = cliente.send(
				HttpRequest.newBuilder(URI.create("https://localhost:" + porta + "/leitos")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(resposta.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
				hsts -> assertThat(hsts).contains("max-age="));
		try (SSLSocket socket = (SSLSocket) confiandoNoCertificado().getSocketFactory().createSocket("localhost", porta)) {
			socket.startHandshake();
			assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
		}
	}

	@Test
	void naoAceitaHttpEmClaroEAceitaTls12ParaClientesMaisAntigos() throws Exception {
		// HTTP em claro na porta TLS: o Tomcat recusa antes da aplicação ("requires TLS"), sem dado nenhum.
		HttpResponse<String> emClaro = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/leitos")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(emClaro.statusCode()).isEqualTo(400);
		assertThat(emClaro.headers().firstValue("Strict-Transport-Security")).isEmpty();

		// TLS 1.1 ou anterior nem é testado aqui: o próprio JDK cliente já o recusa, o teste não provaria nada.
		try (SSLSocket socket = (SSLSocket) confiandoNoCertificado().getSocketFactory().createSocket("localhost", porta)) {
			socket.setEnabledProtocols(new String[] {"TLSv1.2"});
			socket.startHandshake();
			assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.2");
		}
	}

	private static SSLContext confiandoNoCertificado() throws Exception {
		SSLContext contexto = SSLContext.getInstance("TLS");
		contexto.init(null, fabricaDeConfianca().getTrustManagers(), null);
		return contexto;
	}

	private static TrustManagerFactory fabricaDeConfianca() throws Exception {
		KeyStore confiaveis = KeyStore.getInstance("PKCS12");
		try (InputStream entrada = new FileInputStream(keystore.toFile())) {
			confiaveis.load(entrada, SENHA.toCharArray());
		}
		TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		fabrica.init(confiaveis);
		return fabrica;
	}
}
