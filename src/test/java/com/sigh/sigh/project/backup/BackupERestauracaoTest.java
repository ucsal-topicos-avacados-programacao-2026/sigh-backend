package com.sigh.sigh.project.backup;

import com.sigh.sigh.project.comum.cripto.CifraDeDadosClinicos;
import com.sigh.sigh.project.comum.cripto.CifragemDeDadosLegados;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critérios de aceite do BE-09 (RNF-004, RNF-007): backup completo e incrementais cifrados, com cópia
 * fora do servidor; a restauração com dados fictícios confere com a origem; o dado clínico não aparece
 * em texto claro nas cópias nem nos arquivos restaurados. Usa a mesma imagem e os mesmos scripts de
 * produção.
 */
class BackupERestauracaoTest {

	private static final String CHAVE_BACKUP = "chave-ficticia-das-copias-de-teste-com-32+";
	private static final String MARCADOR_CLINICO = "MarcadorClinicoQueNaoPodeVazar";
	private static final String MARCADOR_NOME = "MarcadorNomeEmClaroDeControle";
	private static final String[] TABELAS = {"instituicao", "usuario", "paciente", "prontuario", "registro_clinico"};
	private static final CifraDeDadosClinicos CIFRA = new CifraDeDadosClinicos(
			Base64.getEncoder().encodeToString("chave-ficticia-dos-dados-32bytes".getBytes()), "1", "");

	private static Network rede;
	private static PostgreSQLContainer banco;
	private static GenericContainer<?> executor;
	private static ImageFromDockerfile imagem;
	private static JdbcTemplate jdbc;
	private static CifragemDeDadosLegados cifragemDeLegados;
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
		DriverManagerDataSource fonte = new DriverManagerDataSource(banco.getJdbcUrl(), banco.getUsername(),
				banco.getPassword());
		jdbc = new JdbcTemplate(fonte);
		jdbc.execute("select pg_reload_conf()");
		Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate();
		cifragemDeLegados = new CifragemDeDadosLegados(jdbc,
				new TransactionTemplate(new DataSourceTransactionManager(fonte)), CIFRA);
		instituicaoId = UUID.randomUUID();
		autorId = UUID.randomUUID();
		jdbc.update("insert into instituicao (id, nome, tipo, credenciada) values (?, 'Hospital Fictício', 'PUBLICA', true)",
				instituicaoId);
		jdbc.update("insert into usuario (id, login, nome, senha_hash, mfa_habilitado) values (?, 'medico.ficticio',"
				+ " 'Médico Fictício', 'hash', false)", autorId);

		imagem = new ImageFromDockerfile("sigh-backup-teste", false)
				.withFileFromPath("Dockerfile", Path.of("docker/backup/Dockerfile"))
				.withFileFromPath("backup", Path.of("backup"));
		executor = novoExecutor().withCommand("sleep", "infinity");
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
		// Dado anterior à criptografia, em claro, cifrado pela mesma rotina que a aplicação roda ao subir.
		inserirPaciente(1, false);
		assertThat(cifragemDeLegados.cifrarLegados()).isEqualTo(2);
		rodar("backup-completo.sh");
		inserirPaciente(2, true);
		rodar("backup-incremental.sh");
		inserirPaciente(3, true);
		jdbc.update("update paciente set nome = 'Paciente Alterado " + MARCADOR_NOME + "' where nome like '%1'");
		rodar("backup-incremental.sh");
		String origem = impressaoDigital(null);
		String cadeia = rodar("basename $(cat /backups/cadeia-atual)").getStdout().strip();

		assertThat(rodar("ls /backups/" + cadeia + " && ls /externo/" + cadeia).getStdout())
				.contains("000-completo.tar.gz.gpg", "001-incremental.tar.gz.gpg", "002-incremental.tar.gz.gpg")
				.doesNotContain(".parcial");
		assertThat(encontra(MARCADOR_NOME, "/backups")).as("cópias cifradas não mostram nem o dado não clínico").isFalse();
		decifrarParte(cadeia, "000-completo", "/conferencia/completo");
		decifrarParte(cadeia, "001-incremental", "/conferencia/incremental");
		assertThat(encontra(MARCADOR_NOME, "/conferencia/completo"))
				.as("controle: decifrada, a mesma cópia mostra o dado não clínico").isTrue();
		assertThat(encontra(MARCADOR_CLINICO, "/conferencia")).as("o dado clínico só existe cifrado").isFalse();
		assertThat(rodar("find /conferencia/incremental -name 'INCREMENTAL.*' | wc -l").getStdout().strip())
				.as("a parte 001 é incremental de verdade").isNotEqualTo("0");

		// Recuperação a partir da cópia fora do servidor.
		rodar("restaurar.sh /externo/" + cadeia + " /restaurado");
		// O servidor fica em segundo plano; sem soltar a saída do exec, a sessão do Docker não termina.
		rodar("chown -R postgres:postgres /restaurado && chmod 700 /restaurado"
				+ " && su postgres -s /bin/sh -c \"pg_ctl -D /restaurado -l /tmp/restaurado.log"
				+ " -o '-p 5433 -c listen_addresses=127.0.0.1' start\" </dev/null >/dev/null 2>&1");
		rodar("pg_isready -h 127.0.0.1 -p 5433 -t 60 || (cat /tmp/restaurado.log; exit 1)");

		assertThat(impressaoDigital(5433)).isEqualTo(origem);
		// Grava em disco o que a restauração aplicou do WAL, para a busca olhar os arquivos de verdade.
		rodar("psql -h 127.0.0.1 -p 5433 -U " + banco.getUsername() + " -d " + banco.getDatabaseName() + " -c CHECKPOINT");
		assertThat(encontra(MARCADOR_NOME, "/restaurado/base")).as("controle: a busca acha texto em claro quando ele existe").isTrue();
		assertThat(encontra(MARCADOR_CLINICO, "/restaurado")).as("nem o dado clínico que era legado em claro").isFalse();
	}

	@Test
	void copiaAlteradaNaoEhRestaurada() throws Exception {
		inserirPaciente(9, true);
		rodar("backup-completo.sh");
		rodar("printf 'x' | dd of=$(cat /backups/cadeia-atual)/000-completo.tar.gz.gpg bs=1 seek=200 conv=notrunc");

		ExecResult restauracao = executor.execInContainer("sh", "-c", "restaurar.sh $(cat /backups/cadeia-atual) /adulterado");
		assertThat(restauracao.getExitCode()).isNotZero();
		assertThat(restauracao.getStderr()).contains("cópia alterada ou chave errada");
	}

	/** Um .parcial deixado por uma execução interrompida não trava os backups seguintes. */
	@Test
	void sobraDeExecucaoInterrompidaNaoTravaOIncremental() throws Exception {
		inserirPaciente(8, true);
		rodar("backup-completo.sh");
		String cadeia = rodar("cat /backups/cadeia-atual").getStdout().strip();
		rodar("echo resto > " + cadeia + "/001-incremental.tar.gz.gpg.parcial");

		rodar("backup-incremental.sh");

		assertThat(rodar("ls " + cadeia).getStdout()).contains("001-incremental.tar.gz.gpg").doesNotContain(".parcial");
		rodar("restaurar.sh " + cadeia + " /restaurado-apos-parcial");
	}

	@Test
	void chaveFracaEhRecusadaENaoApareceNaLinhaDeComando() throws Exception {
		ExecResult fraca = executor.execInContainer("sh", "-c", "SIGH_CHAVE_BACKUP=curta backup-completo.sh");
		assertThat(fraca.getExitCode()).isNotZero();
		assertThat(fraca.getStderr()).contains("pelo menos 32 caracteres");

		// Lê a linha de comando de todos os processos durante todo o backup (pg_basebackup e gpg).
		String processos = rodar("backup-completo.sh >/dev/null 2>&1 & backup=$!; while kill -0 $backup 2>/dev/null; do"
				+ " cat /proc/[0-9]*/cmdline 2>/dev/null | tr '\\0' ' '; sleep 0.05; done; wait $backup").getStdout();
		assertThat(processos).contains("pg_basebackup", "gpg").doesNotContain(CHAVE_BACKUP);
	}

	/** A imagem, sem argumentos, sobe o crond com a cadeia semanal e o incremental diário. */
	@Test
	void imagemSobeOAgendadorComCompletoSemanalEIncrementalDiario() throws Exception {
		try (GenericContainer<?> agendador = novoExecutor()) {
			agendador.start();
			ExecResult agenda = agendador.execInContainer("cat", "/etc/crontabs/root");
			assertThat(agenda.getStdout())
					.contains("0 2 * * 0   . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-completo.sh")
					.contains("0 2 * * 1-6 . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-incremental.sh");
			assertThat(agendador.execInContainer("pgrep", "crond").getExitCode()).isZero();
			assertThat(agendador.execInContainer("stat", "-c", "%a", "/etc/sigh-backup/ambiente").getStdout().strip())
					.isEqualTo("600");
			assertThat(agendador.execInContainer("sh", "-c", ". /etc/sigh-backup/ambiente && echo $PGHOST").getStdout().strip())
					.isEqualTo("banco");
		}
	}

	private static GenericContainer<?> novoExecutor() {
		return new GenericContainer<>(imagem)
				.withNetwork(rede)
				.withEnv("PGHOST", "banco").withEnv("PGPORT", "5432")
				.withEnv("PGUSER", banco.getUsername()).withEnv("PGPASSWORD", banco.getPassword())
				.withEnv("SIGH_BACKUP_DIR", "/backups").withEnv("SIGH_BACKUP_DESTINO_EXTERNO", "/externo")
				.withEnv("SIGH_CHAVE_BACKUP", CHAVE_BACKUP);
	}

	private static void decifrarParte(String cadeia, String parte, String destino) throws Exception {
		rodar("mkdir -p " + destino + " && printf '%s' \"$SIGH_CHAVE_BACKUP\" > /tmp/k && gpg --batch --quiet"
				+ " --pinentry-mode loopback --passphrase-file /tmp/k --decrypt /backups/" + cadeia + "/" + parte
				+ ".tar.gz.gpg | gzip -dc | tar -C " + destino + " -xf - && rm -f /tmp/k");
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

	/** Paciente com um diagnóstico; {@code cifrado} falso simula dado gravado antes da criptografia. */
	private static void inserirPaciente(int numero, boolean cifrado) {
		UUID paciente = UUID.randomUUID();
		UUID prontuario = UUID.randomUUID();
		jdbc.update("insert into paciente (id, cpf, nome, data_nascimento) values (?, ?, ?, ?)", paciente,
				String.format("%011d", 90_000_000_000L + numero), "Paciente Fictício " + MARCADOR_NOME + " " + numero,
				LocalDate.of(1980, 1, numero));
		jdbc.update("insert into prontuario (id, paciente_id, criado_em) values (?, ?, ?)", prontuario, paciente,
				LocalDateTime.of(2026, 11, 1, 8, 0));
		String conteudo = MARCADOR_CLINICO + " episódio depressivo " + numero;
		String cid = "F32." + numero;
		jdbc.update("""
				insert into registro_clinico (id, prontuario_id, tipo, criado_em, conteudo, categoria_informacao,
				  codigo_cid10, autor_id, origem_id) values (?, ?, 'DIAGNOSTICO', ?, ?, 'Saúde mental', ?, ?, ?)
				""", UUID.randomUUID(), prontuario, LocalDateTime.of(2026, 11, 1, 9, 0),
				cifrado ? CIFRA.cifrar(conteudo) : conteudo, cifrado ? CIFRA.cifrar(cid) : cid, autorId, instituicaoId);
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
			return jdbc.queryForObject(consulta.toString(), String.class);
		}
		return rodar("psql -h 127.0.0.1 -p " + portaRestaurada + " -U " + banco.getUsername() + " -d "
				+ banco.getDatabaseName() + " -Atc \"" + consulta + "\"").getStdout().strip();
	}
}
