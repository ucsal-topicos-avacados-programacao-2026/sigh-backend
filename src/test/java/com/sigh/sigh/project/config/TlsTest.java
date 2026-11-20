package com.sigh.sigh.project.config;

import com.sigh.sigh.project.SighProjectApplication;
import com.sigh.sigh.project.support.BancoDoSigh;
import com.sigh.sigh.project.support.CertificadoDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import javax.net.ssl.SSLSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF-004 no perfil tls (BE-09 e BE-22):
 * <ul>
 * <li>o back-end só fala HTTPS, com TLS 1.2 ou 1.3, e manda o navegador usar sempre HTTPS (HSTS);</li>
 * <li>o banco só aceita conexão com TLS, e o back-end só conecta a ele com TLS e com o certificado do
 * servidor conferido (sslmode=verify-full).</li>
 * </ul>
 * Os certificados são fictícios, gerados na hora e entregues em PEM, como em produção. O banco é a
 * imagem do SIGH subindo com {@code iniciar-banco.sh}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tls")
class TlsTest {

	private static CertificadoDeTeste certificadoDoBackEnd;
	private static CertificadoDeTeste certificadoDoBanco;
	private static GenericContainer<?> banco;

	@LocalServerPort
	int porta;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeAll
	static void subirBancoComTls() throws Exception {
		certificadoDoBackEnd = CertificadoDeTeste.gerar("dns:localhost", "ip:127.0.0.1");
		certificadoDoBanco = BancoDoSigh.certificado();
		banco = BancoDoSigh.comTls(certificadoDoBanco);
		banco.start();
	}

	@AfterAll
	static void derrubarBanco() {
		if (banco != null) {
			banco.stop();
		}
	}

	@DynamicPropertySource
	static void configuracao(DynamicPropertyRegistry propriedades) {
		propriedades.add("SIGH_TLS_CERTIFICADO", () -> "file:" + certificadoDoBackEnd.certificadoPem());
		propriedades.add("SIGH_TLS_CHAVE_PRIVADA", () -> "file:" + certificadoDoBackEnd.chavePem());
		propriedades.add("spring.datasource.url", () -> BancoDoSigh.urlComTls(banco, certificadoDoBanco));
		propriedades.add("spring.datasource.username", () -> BancoDoSigh.USUARIO);
		propriedades.add("spring.datasource.password", () -> BancoDoSigh.SENHA);
		// Fixa a IA na própria máquina: um SIGH_IA_URL remoto no ambiente de quem roda os testes derrubaria a classe.
		propriedades.add("sigh.ia.url", () -> "http://localhost:11434");
	}

	@Test
	void respondeSoPorHttpsComHstsETls13() throws Exception {
		HttpClient cliente = HttpClient.newBuilder().sslContext(certificadoDoBackEnd.contextoQueConfia()).build();
		HttpResponse<String> resposta = cliente.send(
				HttpRequest.newBuilder(URI.create("https://localhost:" + porta + "/leitos")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(resposta.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
				hsts -> assertThat(hsts).contains("max-age="));
		try (SSLSocket socket = (SSLSocket) certificadoDoBackEnd.contextoQueConfia().getSocketFactory()
				.createSocket("localhost", porta)) {
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

		// TLS 1.1 ou anterior nem é testado aqui: o próprio JDK cliente já o recusa, o teste não provaria nada.
		try (SSLSocket socket = (SSLSocket) certificadoDoBackEnd.contextoQueConfia().getSocketFactory()
				.createSocket("localhost", porta)) {
			socket.setEnabledProtocols(new String[] {"TLSv1.2"});
			socket.startHandshake();
			assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.2");
		}
	}

	/** A aplicação subiu (Flyway e JPA rodaram) e a sessão dela com o banco está cifrada. */
	@Test
	void aplicacaoFalaComOBancoSoPorTls() {
		Map<String, Object> sessao = jdbc.queryForMap("select ssl, version from pg_stat_ssl where pid = pg_backend_pid()");

		assertThat(sessao.get("ssl")).isEqualTo(true);
		assertThat(sessao.get("version")).isEqualTo("TLSv1.3");
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
	}

	/** Critério de aceite do BE-22: conexão sem TLS com o banco é recusada (pelo próprio banco). */
	@Test
	void bancoRecusaConexaoSemTls() {
		assertThatThrownBy(() -> DriverManager.getConnection(BancoDoSigh.urlSemSslmode(banco) + "?sslmode=disable",
				BancoDoSigh.USUARIO, BancoDoSigh.SENHA).close())
				.hasMessageContaining("pg_hba.conf rejects connection")
				.hasMessageContaining("no encryption");
	}

	/** verify-full confere o certificado: um banco com certificado em que o cliente não confia é recusado. */
	@Test
	void certificadoDoBancoQueNaoEhOEsperadoEhRecusado() throws Exception {
		CertificadoDeTeste outro = CertificadoDeTeste.gerar("dns:localhost", "ip:127.0.0.1");
		String url = BancoDoSigh.urlSemSslmode(banco) + "?sslmode=verify-full&sslrootcert=" + outro.certificadoPem();

		assertThatThrownBy(() -> DriverManager.getConnection(url, BancoDoSigh.USUARIO, BancoDoSigh.SENHA).close())
				.hasMessageContaining("SSL");
	}

	/** No perfil tls, a aplicação não sobe com uma URL que não confere o certificado do banco. */
	@Test
	void semVerifyFullAAplicacaoNaoSobe() throws Exception {
		ConexaoSeguraComOBanco exigindo = new ConexaoSeguraComOBanco(new MockEnvironment().withProperty("sigh.banco.exigir-tls", "true"));
		ConexaoSeguraComOBanco semExigir = new ConexaoSeguraComOBanco(new MockEnvironment());

		for (String modo : new String[] {"require", "verify-ca", "prefer"}) {
			try (HikariDataSource fonte = fonte(BancoDoSigh.urlSemSslmode(banco) + "?sslmode=" + modo)) {
				assertThatThrownBy(() -> exigindo.postProcessAfterInitialization(fonte, "dataSource"))
						.as(modo).hasMessageContaining("sslmode=verify-full");
				assertThatCode(() -> semExigir.postProcessAfterInitialization(fonte, "dataSource")).doesNotThrowAnyException();
			}
		}
		try (HikariDataSource fonte = fonte(BancoDoSigh.urlComTls(banco, certificadoDoBanco))) {
			assertThatCode(() -> exigindo.postProcessAfterInitialization(fonte, "dataSource")).doesNotThrowAnyException();
		}
		assertThat(ConexaoSeguraComOBanco.pedeVerificacaoCompleta("jdbc:postgresql://h/b?sslmode=verify-full&sslmode=disable"))
				.as("um segundo sslmode poderia desligar o primeiro").isFalse();
		assertThat(ConexaoSeguraComOBanco.pedeVerificacaoCompleta("jdbc:postgresql://h/b")).isFalse();
		assertThat(ConexaoSeguraComOBanco.pedeVerificacaoCompleta(
				"jdbc:postgresql://h/b?sslmode=verify-full&sslfactory=org.postgresql.ssl.NonValidatingFactory"))
				.as("a fábrica que não valida o certificado desligaria o verify-full").isFalse();
		assertThat(ConexaoSeguraComOBanco.pedeVerificacaoCompleta("jdbc:postgresql://h/b?sslmode=verify-full&sslHostnameVerifier=x"))
				.isFalse();
		try (HikariDataSource fonte = fonte(BancoDoSigh.urlComTls(banco, certificadoDoBanco))) {
			fonte.addDataSourceProperty("sslmode", "disable");
			assertThatThrownBy(() -> exigindo.postProcessAfterInitialization(fonte, "dataSource"))
					.hasMessageContaining("fora da URL");
		}
	}

	/**
	 * A aplicação de verdade, no perfil tls e com uma URL sem verify-full, não sobe, e para antes de o
	 * Flyway tocar no banco.
	 */
	@Test
	void aplicacaoNoPerfilTlsNaoSobeSemVerifyFullENemMigraOBanco() throws Exception {
		jdbc.execute("create database sigh_intocado");
		try {
			String url = BancoDoSigh.urlSemSslmode(banco).replace("/" + BancoDoSigh.BANCO, "/sigh_intocado")
					+ "?sslmode=require";
			assertThatThrownBy(() -> new SpringApplicationBuilder(SighProjectApplication.class).profiles("tls")
					.properties("spring.datasource.url=" + url, "spring.datasource.username=" + BancoDoSigh.USUARIO,
							"spring.datasource.password=" + BancoDoSigh.SENHA, "server.port=0",
							"SIGH_TLS_CERTIFICADO=file:" + certificadoDoBackEnd.certificadoPem(),
							"SIGH_TLS_CHAVE_PRIVADA=file:" + certificadoDoBackEnd.chavePem())
					.run().close())
					.hasStackTraceContaining("sslmode=verify-full");
			try (Connection intocado = DriverManager.getConnection(url.replace("sslmode=require", "sslmode=verify-full&sslrootcert="
					+ certificadoDoBanco.certificadoPem()), BancoDoSigh.USUARIO, BancoDoSigh.SENHA);
					ResultSet tabela = intocado.createStatement().executeQuery("select to_regclass('flyway_schema_history')")) {
				tabela.next();
				assertThat(tabela.getString(1)).as("o Flyway não rodou").isNull();
			}
		} finally {
			jdbc.execute("drop database sigh_intocado");
		}
	}

	/**
	 * O serviço de IA é ponto de decifragem (RNF-004): no perfil tls, a aplicação de verdade não sobe com a IA
	 * num host que não foi autorizado, nem com ela fora da máquina sem HTTPS.
	 */
	@Test
	void aplicacaoNoPerfilTlsNaoSobeComIAForaDosDestinosAutorizados() {
		for (String[] caso : new String[][] {
				{"https://ia.externa.example", "", "não está em sigh.ia.hosts-autorizados"},
				{"http://ia.rede.example", "ia.rede.example", "sigh.ia.url precisa usar HTTPS"}}) {
			// Pela linha de comando, para valer acima do DB_URL e do SIGH_IA_* do ambiente de quem roda os testes.
			assertThatThrownBy(() -> new SpringApplicationBuilder(SighProjectApplication.class).profiles("tls")
					.run("--spring.datasource.url=" + BancoDoSigh.urlComTls(banco, certificadoDoBanco),
							"--spring.datasource.username=" + BancoDoSigh.USUARIO,
							"--spring.datasource.password=" + BancoDoSigh.SENHA, "--server.port=0",
							"--SIGH_TLS_CERTIFICADO=file:" + certificadoDoBackEnd.certificadoPem(),
							"--SIGH_TLS_CHAVE_PRIVADA=file:" + certificadoDoBackEnd.chavePem(),
							"--sigh.ia.url=" + caso[0], "--sigh.ia.hosts-autorizados=" + caso[1])
					.close())
					.as(caso[0])
					.hasStackTraceContaining(caso[2]);
		}
	}

	private static HikariDataSource fonte(String url) {
		HikariDataSource fonte = new HikariDataSource();
		fonte.setJdbcUrl(url);
		fonte.setUsername(BancoDoSigh.USUARIO);
		fonte.setPassword(BancoDoSigh.SENHA);
		return fonte;
	}
}
