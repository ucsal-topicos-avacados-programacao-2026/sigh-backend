package com.sigh.sigh.project.support;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public record CertificadoDeTeste(Path keystore, Path certificadoPem, Path chavePem) {

	private static final String SENHA = "senha-ficticia-do-teste";

	public static CertificadoDeTeste gerar(String... nomes) throws Exception {
		Path keystore = Files.createTempDirectory("sigh-tls").resolve("certificado.p12");
		Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
				"-genkeypair", "-alias", "sigh", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "2",
				"-dname", "CN=" + nomes[0].substring(nomes[0].indexOf(':') + 1), "-ext", "SAN=" + String.join(",", nomes),
				"-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", SENHA, "-keypass", SENHA)
				.redirectErrorStream(true).start();
		String saida = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(keytool.waitFor()).as(saida).isZero();

		KeyStore p12 = carregar(keystore);
		Path certificado = keystore.resolveSibling("certificado.pem");
		Path chave = keystore.resolveSibling("chave.pem");
		Files.writeString(certificado, pem("CERTIFICATE", p12.getCertificate("sigh").getEncoded()));
		Files.writeString(chave, pem("PRIVATE KEY", p12.getKey("sigh", SENHA.toCharArray()).getEncoded()));
		return new CertificadoDeTeste(keystore, certificado, chave);
	}

	public SSLContext contextoQueConfia() throws Exception {
		TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		fabrica.init(carregar(keystore));
		SSLContext contexto = SSLContext.getInstance("TLS");
		contexto.init(null, fabrica.getTrustManagers(), null);
		return contexto;
	}

	private static KeyStore carregar(Path keystore) throws Exception {
		KeyStore p12 = KeyStore.getInstance("PKCS12");
		try (InputStream entrada = new FileInputStream(keystore.toFile())) {
			p12.load(entrada, SENHA.toCharArray());
		}
		return p12;
	}

	private static String pem(String tipo, byte[] conteudo) {
		return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(conteudo)
				+ "\n-----END " + tipo + "-----\n";
	}
}
