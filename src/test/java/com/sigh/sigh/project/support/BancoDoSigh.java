package com.sigh.sigh.project.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * O PostgreSQL 17 do SIGH como em produção: a imagem {@code docker/backup} subindo com
 * {@code iniciar-banco.sh}, só com TLS (sem TLS, a conexão é recusada) e com o certificado fictício.
 */
public final class BancoDoSigh {

	public static final String USUARIO = "sigh";
	public static final String SENHA = "senha-ficticia-do-banco-de-teste";
	public static final String BANCO = "sigh";
	private static ImageFromDockerfile imagem;

	private BancoDoSigh() {
	}

	/** Imagem montada a partir do Dockerfile e dos scripts do repositório (a mesma de produção). */
	public static synchronized ImageFromDockerfile imagem() {
		if (imagem == null) {
			imagem = new ImageFromDockerfile("sigh-backup-teste", false)
					.withFileFromPath("Dockerfile", Path.of("docker/backup/Dockerfile"))
					.withFileFromPath("backup", Path.of("backup"));
		}
		return imagem;
	}

	/** Certificado do servidor válido para o endereço do Docker visto daqui e para o apelido "banco". */
	public static CertificadoDeTeste certificado() throws Exception {
		String host = DockerClientFactory.instance().dockerHostIpAddress();
		String daqui = host.matches("[0-9.]+") ? "ip:" + host : "dns:" + host;
		return CertificadoDeTeste.gerar("dns:banco", daqui, "dns:localhost", "ip:127.0.0.1");
	}

	public static GenericContainer<?> comTls(CertificadoDeTeste certificado) {
		return new GenericContainer<>(imagem())
				.withEnv("POSTGRES_USER", USUARIO).withEnv("POSTGRES_PASSWORD", SENHA).withEnv("POSTGRES_DB", BANCO)
				.withEnv("SIGH_TLS_CERTIFICADO_BANCO", "/certs/servidor.crt")
				.withEnv("SIGH_TLS_CHAVE_BANCO", "/certs/servidor.key")
				.withCopyFileToContainer(MountableFile.forHostPath(certificado.certificadoPem(), 0644), "/certs/servidor.crt")
				.withCopyFileToContainer(MountableFile.forHostPath(certificado.chavePem(), 0600), "/certs/servidor.key")
				.withCommand("iniciar-banco.sh")
				.withExposedPorts(5432)
				.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2)
						.withStartupTimeout(Duration.ofMinutes(3)));
	}

	/** URL JDBC com TLS e o certificado do servidor conferido, como a de produção. */
	public static String urlComTls(GenericContainer<?> banco, CertificadoDeTeste certificado) {
		return urlSemSslmode(banco) + "?sslmode=verify-full&sslrootcert=" + certificado.certificadoPem();
	}

	public static String urlSemSslmode(GenericContainer<?> banco) {
		return "jdbc:postgresql://" + banco.getHost() + ":" + banco.getMappedPort(5432) + "/" + BANCO;
	}
}
