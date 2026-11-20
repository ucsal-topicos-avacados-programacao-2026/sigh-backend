package com.sigh.sigh.project.backup;

import com.sigh.sigh.project.comum.cripto.CifraDeDadosClinicos;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critérios de aceite do BE-09 (RNF-004, RNF-007): backup completo e incrementais cifrados; a
 * restauração com dados fictícios confere com a origem; o dado clínico não aparece em texto claro
 * nas cópias nem nos arquivos restaurados. Usa a mesma imagem e os mesmos scripts de produção.
 */
class BackupERestauracaoTest {

	private static final String CHAVE_BACKUP = "chave-ficticia-das-copias-de-teste";
	private static final String MARCADOR_CLINICO = "MarcadorClinicoQueNaoPodeVazar";
	private static final String MARCADOR_NOME = "MarcadorNomeEmClaroDeControle";
	private static final String[] TABELAS = {"instituicao", "usuario", "paciente", "prontuario", "registro_clinico"};
	private static final CifraDeDadosClinicos CIFRA = new CifraDeDadosClinicos(
			Base64.getEncoder().encodeToString("chave-ficticia-dos-dados-32bytes".getBytes()), "1", "");

	private static Network rede;
	private static PostgreSQLContainer banco;
	private static GenericContainer<?> executor;
	private static UUID instituicaoId;
	private static UUID autorId;

	@BeforeAll
	static void subirBancoEExecutor() throws Exception {
		rede = Network.newNetwork();
		banco = new PostgreSQLContainer("postgres:17-alpine")
				.withNetwork(rede).withNetworkAliases("banco")
				.withCommand("postgres", "-c", "summarize_wal=on", "-c", "wal_level=replica");
		banco.start();
		// Usuário de backup precisa de conexão de replicação (pg_basebackup).
		banco.execInContainer("sh", "-c",
				"echo 'host replication all all scram-sha-256' >> /var/lib/postgresql/data/pg_hba.conf");
		executarSql("select pg_reload_conf()");
		Flyway.configure().dataSource(banco.getJdbcUrl(), banco.getUsername(), banco.getPassword())
				.locations("classpath:db/migration").load().migrate();
		instituicaoId = UUID.randomUUID();
		autorId = UUID.randomUUID();
		executarSql("insert into instituicao (id, nome, tipo, credenciada) values ('" + instituicaoId
				+ "', 'Hospital Fictício', 'PUBLICA', true)");
		executarSql("insert into usuario (id, login, nome, senha_hash, mfa_habilitado) values ('" + autorId
				+ "', 'medico.ficticio', 'Médico Fictício', 'hash', false)");

		executor = new GenericContainer<>(new ImageFromDockerfile("sigh-backup-teste", false)
				.withFileFromPath("Dockerfile", Path.of("docker/backup/Dockerfile"))
				.withFileFromPath("backup", Path.of("backup")))
				.withNetwork(rede)
				.withEnv("PGHOST", "banco").withEnv("PGPORT", "5432")
				.withEnv("PGUSER", banco.getUsername()).withEnv("PGPASSWORD", banco.getPassword())
				.withEnv("SIGH_BACKUP_DIR", "/backups").withEnv("SIGH_CHAVE_BACKUP", CHAVE_BACKUP)
				.withCommand("sleep", "infinity");
		executor.start();
	}

	@AfterAll
	static void derrubar() {
		if (executor != null) {
			executor.stop();
		}
		if (banco != null) {
			banco.stop();
		}
		if (rede != null) {
			rede.close();
		}
	}

	@Test
	void cadeiaCompletaMaisIncrementaisRestauraIgualAOrigemSemTextoClaro() throws Exception {
		inserirPaciente(1);
		rodar("backup-completo.sh");
		inserirPaciente(2);
		rodar("backup-incremental.sh");
		inserirPaciente(3);
		executarSql("update paciente set nome = 'Paciente Alterado " + MARCADOR_NOME + "' where nome like '%1'");
		rodar("backup-incremental.sh");
		String origem = impressaoDigital(null);

		assertThat(rodar("ls /backups/$(basename $(cat /backups/cadeia-atual))").getStdout())
				.contains("00-completo.tar.gz.enc", "01-incremental.tar.gz.enc", "02-incremental.tar.gz.enc");
		assertThat(encontra(MARCADOR_NOME, "/backups")).as("cópias cifradas não mostram nem o dado não clínico").isFalse();

		rodar("restaurar.sh $(cat /backups/cadeia-atual) /restaurado");
		// O servidor fica em segundo plano; sem soltar a saída do exec, a sessão do Docker não termina.
		rodar("chown -R postgres:postgres /restaurado && chmod 700 /restaurado"
				+ " && su postgres -s /bin/sh -c \"pg_ctl -D /restaurado -l /tmp/restaurado.log"
				+ " -o '-p 5433 -c listen_addresses=127.0.0.1' start\" </dev/null >/dev/null 2>&1");
		rodar("pg_isready -h 127.0.0.1 -p 5433 -t 60");

		assertThat(impressaoDigital(5433)).isEqualTo(origem);
		// Grava em disco o que a restauração aplicou do WAL, para a busca olhar os arquivos de verdade.
		rodar("psql -h 127.0.0.1 -p 5433 -U " + banco.getUsername() + " -d " + banco.getDatabaseName() + " -c CHECKPOINT");
		assertThat(encontra(MARCADOR_NOME, "/restaurado/base")).as("controle: a busca acha texto em claro quando ele existe").isTrue();
		assertThat(encontra(MARCADOR_CLINICO, "/restaurado")).as("o dado clínico só existe cifrado").isFalse();
	}

	@Test
	void copiaAlteradaNaoEhRestaurada() throws Exception {
		inserirPaciente(9);
		rodar("backup-completo.sh");
		rodar("printf 'x' | dd of=$(cat /backups/cadeia-atual)/00-completo.tar.gz.enc bs=1 seek=100 conv=notrunc");

		ExecResult restauracao = executor.execInContainer("sh", "-c", "restaurar.sh $(cat /backups/cadeia-atual) /adulterado");
		assertThat(restauracao.getExitCode()).isNotZero();
		assertThat(restauracao.getStderr()).contains("HMAC não confere");
	}

	/**
	 * Busca em bytes (LC_ALL=C): com a localidade UTF-8 da imagem, o grep do BusyBox não acha texto em
	 * arquivo binário que tenha acentos, o que daria falso "não encontrado".
	 */
	private static boolean encontra(String texto, String pasta) throws Exception {
		ExecResult busca = executor.execInContainer("sh", "-c", "LC_ALL=C grep -r -a -l -F '" + texto + "' " + pasta);
		assertThat(busca.getExitCode()).as(busca.getStderr()).isIn(0, 1);
		return busca.getExitCode() == 0;
	}

	private static ExecResult rodar(String comando) throws Exception {
		ExecResult resultado = executor.execInContainer("sh", "-c", comando);
		assertThat(resultado.getExitCode()).as(comando + "\n" + resultado.getStderr()).isZero();
		return resultado;
	}

	private static void inserirPaciente(int numero) throws Exception {
		try (Connection conexao = conectar()) {
			UUID paciente = UUID.randomUUID();
			UUID prontuario = UUID.randomUUID();
			try (PreparedStatement sql = conexao.prepareStatement(
					"insert into paciente (id, cpf, nome, data_nascimento) values (?, ?, ?, ?)")) {
				sql.setObject(1, paciente);
				sql.setString(2, String.format("%011d", 90_000_000_000L + numero));
				sql.setString(3, "Paciente Fictício " + MARCADOR_NOME + " " + numero);
				sql.setObject(4, LocalDate.of(1980, 1, numero));
				sql.executeUpdate();
			}
			try (PreparedStatement sql = conexao.prepareStatement(
					"insert into prontuario (id, paciente_id, criado_em) values (?, ?, ?)")) {
				sql.setObject(1, prontuario);
				sql.setObject(2, paciente);
				sql.setObject(3, LocalDateTime.of(2026, 11, 1, 8, 0));
				sql.executeUpdate();
			}
			try (PreparedStatement sql = conexao.prepareStatement("""
					insert into registro_clinico (id, prontuario_id, tipo, criado_em, conteudo, categoria_informacao,
					  codigo_cid10, autor_id, origem_id) values (?, ?, 'DIAGNOSTICO', ?, ?, 'Saúde mental', ?, ?, ?)
					""")) {
				sql.setObject(1, UUID.randomUUID());
				sql.setObject(2, prontuario);
				sql.setObject(3, LocalDateTime.of(2026, 11, 1, 9, 0));
				sql.setString(4, CIFRA.cifrar(MARCADOR_CLINICO + " episódio depressivo " + numero));
				sql.setString(5, CIFRA.cifrar("F32." + numero));
				sql.setObject(6, autorId);
				sql.setObject(7, instituicaoId);
				sql.executeUpdate();
			}
		}
	}

	/** md5 de todas as linhas das tabelas, em ordem: igual na origem e na cópia restaurada. */
	private static String impressaoDigital(Integer portaRestaurada) throws Exception {
		StringBuilder consulta = new StringBuilder("select concat_ws(',' ");
		for (String tabela : TABELAS) {
			consulta.append(", (select md5(coalesce(string_agg(t::text, '|' order by t.id), '')) from ")
					.append(tabela).append(" t)");
		}
		consulta.append(")");
		if (portaRestaurada == null) {
			try (Connection conexao = conectar(); Statement sql = conexao.createStatement();
					ResultSet linha = sql.executeQuery(consulta.toString())) {
				linha.next();
				return linha.getString(1);
			}
		}
		return rodar("psql -h 127.0.0.1 -p " + portaRestaurada + " -U " + banco.getUsername() + " -d "
				+ banco.getDatabaseName() + " -Atc \"" + consulta + "\"").getStdout().strip();
	}

	private static void executarSql(String comando) throws Exception {
		try (Connection conexao = conectar(); Statement sql = conexao.createStatement()) {
			sql.execute(comando);
		}
	}

	private static Connection conectar() throws Exception {
		return DriverManager.getConnection(banco.getJdbcUrl(), banco.getUsername(), banco.getPassword());
	}
}
