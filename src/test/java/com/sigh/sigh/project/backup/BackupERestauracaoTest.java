package com.sigh.sigh.project.backup;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.sigh.sigh.project.comum.cripto.CifraDeDadosClinicos;
import com.sigh.sigh.project.comum.cripto.ManutencaoDaCifragem;
import com.sigh.sigh.project.support.BancoDoSigh;
import com.sigh.sigh.project.support.CertificadoDeTeste;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackupERestauracaoTest {

	private static final String CHAVE_BACKUP = "chave-ficticia-das-copias-de-teste-com-32+";
	private static final String MARCADOR_CLINICO = "MarcadorClinicoQueNaoPodeVazar";
	private static final String MARCADOR_NOME = "MarcadorNomeEmClaroDeControle";
	private static final String[] TABELAS = {"instituicao", "usuario", "paciente", "prontuario", "registro_clinico"};
	private static final CifraDeDadosClinicos CIFRA = new CifraDeDadosClinicos(
			Base64.getEncoder().encodeToString("chave-ficticia-dos-dados-32bytes".getBytes()), "1", "");
	private static final String VOLUME_WAL = "sigh-wal-teste-" + UUID.randomUUID();

	private static Network rede;
	private static CertificadoDeTeste certificado;
	private static GenericContainer<?> banco;
	private static GenericContainer<?> executor;
	private static JdbcTemplate jdbc;
	private static ManutencaoDaCifragem manutencao;
	private static UUID instituicaoId;
	private static UUID autorId;

	@BeforeAll
	static void subirBancoEExecutor() throws Exception {
		rede = Network.newNetwork();
		certificado = BancoDoSigh.certificado();
		banco = BancoDoSigh.comTls(certificado)
				.withNetwork(rede).withNetworkAliases("banco")
				.withEnv("SIGH_WAL_DIR", "/wal").withEnv("SIGH_CHAVE_BACKUP", CHAVE_BACKUP)
				.withCreateContainerCmdModifier(comando -> comando.getHostConfig()
						.withBinds(new Bind(VOLUME_WAL, new Volume("/wal"))));
		banco.start();
		DriverManagerDataSource fonte = new DriverManagerDataSource(BancoDoSigh.urlComTls(banco, certificado),
				BancoDoSigh.USUARIO, BancoDoSigh.SENHA);
		jdbc = new JdbcTemplate(fonte);
		Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate();
		manutencao = new ManutencaoDaCifragem(jdbc, new TransactionTemplate(new DataSourceTransactionManager(fonte)),
				CIFRA, Clock.systemUTC());
		instituicaoId = UUID.randomUUID();
		autorId = UUID.randomUUID();
		jdbc.update("insert into instituicao (id, nome, tipo, credenciada) values (?, 'Hospital Fictício', 'PUBLICA', true)",
				instituicaoId);
		jdbc.update("insert into usuario (id, login, nome, senha_hash, mfa_habilitado) values (?, 'medico.ficticio',"
				+ " 'Médico Fictício', 'hash', false)", autorId);

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
		DockerClientFactory.instance().client().removeVolumeCmd(VOLUME_WAL).exec();
	}

	@Test
	void cadeiaCompletaMaisIncrementaisEWalRestauraIgualAOrigemSemTextoClaro() throws Exception {
		inserirPaciente(1, false);
		assertThat(manutencao.executar().cifrados()).isEqualTo(2);
		arquivarWalAtual();
		assertThat(walDecifradoContem(MARCADOR_CLINICO)).as("controle: o WAL de antes da migração tem o dado em claro").isTrue();
		rodar("backup-completo.sh --descartar-anteriores");
		assertThat(walDecifradoContem(MARCADOR_CLINICO)).as("o WAL de antes da migração foi descartado").isFalse();
		inserirPaciente(2, true);
		rodar("backup-incremental.sh");
		inserirPaciente(3, true);
		jdbc.update("update paciente set nome = 'Paciente Alterado " + MARCADOR_NOME + "' where nome like '%1'");
		rodar("backup-incremental.sh");
		inserirPaciente(4, true);
		String segmento = arquivarWalAtual();
		String origem = impressaoDigital(null);
		String cadeia = rodar("basename $(cat /backups/cadeia-atual)").getStdout().strip();

		assertThat(rodar("ls /backups/" + cadeia + " && ls /externo/" + cadeia).getStdout())
				.contains("000-completo.tar.gz.gpg", "001-incremental.tar.gz.gpg", "002-incremental.tar.gz.gpg")
				.doesNotContain(".parcial");
		assertThat(encontra(MARCADOR_NOME, "/backups")).as("cópias cifradas não mostram nem o dado não clínico").isFalse();
		assertThat(banco.execInContainer("sh", "-c", "LC_ALL=C grep -r -a -l -F '" + MARCADOR_NOME + "' "
				+ "/var/lib/postgresql/data/pg_wal").getExitCode()).as("controle: o WAL do servidor tem o dado em claro").isZero();
		assertThat(encontra(MARCADOR_NOME, "/wal")).as("o WAL arquivado não mostra o dado").isFalse();
		assertThat(executor.execInContainer("gzip", "-t", "/wal/" + segmento + ".gpg").getExitCode())
				.as("sem a chave, o segmento arquivado nem é um gzip").isNotZero();
		assertThat(walDecifradoContem(MARCADOR_NOME + " 4")).as("controle: decifrado, o WAL tem o paciente 4").isTrue();
		assertThat(walDecifradoContem(MARCADOR_CLINICO)).as("nem decifrado o WAL guardado tem o dado clínico").isFalse();
		assertThat(rodar("ls /externo/wal").getStdout()).as("o WAL arquivado também vai para fora").contains(".gpg");
		decifrarParte(cadeia, "000-completo", "/conferencia/completo");
		decifrarParte(cadeia, "001-incremental", "/conferencia/incremental");
		assertThat(encontra(MARCADOR_NOME, "/conferencia/completo"))
				.as("controle: decifrada, a mesma cópia mostra o dado não clínico").isTrue();
		assertThat(encontra(MARCADOR_CLINICO, "/conferencia")).as("o dado clínico só existe cifrado").isFalse();
		assertThat(rodar("find /conferencia/incremental -name 'INCREMENTAL.*' | wc -l").getStdout().strip())
				.as("a parte 001 é incremental de verdade").isNotEqualTo("0");

		rodar("restaurar.sh /externo/" + cadeia + " /restaurado /wal");
		subirRestaurado("/restaurado", 5433);

		assertThat(impressaoDigital(5433)).isEqualTo(origem);
		assertThat(psql(5433, "select count(*) from paciente where nome like '%" + MARCADOR_NOME + " 4'"))
				.as("o paciente gravado depois do último backup voltou pelo WAL").isEqualTo("1");
		psql(5433, "CHECKPOINT");
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

		String processos = rodar("backup-completo.sh >/dev/null 2>&1 & backup=$!; while kill -0 $backup 2>/dev/null; do"
				+ " cat /proc/[0-9]*/cmdline 2>/dev/null | tr '\\0' ' '; sleep 0.05; done; wait $backup").getStdout();
		assertThat(processos).contains("pg_basebackup", "gpg").doesNotContain(CHAVE_BACKUP);
	}

	@Test
	void backupSoConectaAoBancoComTlsVerificado() throws Exception {
		ExecResult semTls = executor.execInContainer("sh", "-c",
				"PGSSLMODE=disable pg_basebackup -D /tmp/sem-tls -Fp -X none");
		assertThat(semTls.getExitCode()).isNotZero();
		assertThat(semTls.getStderr()).contains("pg_hba.conf rejects", "no encryption");

		ExecResult semVerificar = executor.execInContainer("sh", "-c", "PGSSLMODE=require backup-completo.sh");
		assertThat(semVerificar.getExitCode()).isNotZero();
		assertThat(semVerificar.getStderr()).contains("PGSSLMODE=verify-full");

		assertThat(rodar("psql -d " + BancoDoSigh.BANCO + " -Atc \"select ssl from pg_stat_ssl where pid = pg_backend_pid()\"")
				.getStdout().strip()).isEqualTo("t");
	}

	@Test
	void alertaAcusaBackupQueFalhouFoiPuladoOuEstaAtrasado() throws Exception {
		String pasta = "SIGH_BACKUP_DIR=/backups-alerta SIGH_BACKUP_DESTINO_EXTERNO= SIGH_WAL_DIR= ";
		ExecResult nenhum = executor.execInContainer("sh", "-c", pasta + "verificar-backup.sh");
		assertThat(nenhum.getExitCode()).isNotZero();
		assertThat(nenhum.getStdout()).contains("nenhum backup concluído");

		rodar(pasta + "backup-incremental.sh");
		assertThat(rodar("ls $(cat /backups-alerta/cadeia-atual)").getStdout()).contains("000-completo.tar.gz.gpg");
		assertThat(rodar(pasta + "verificar-backup.sh").getStdout()).contains("ok");

		ExecResult falha = executor.execInContainer("sh", "-c", pasta + "PGPASSWORD=senha-errada backup-incremental.sh");
		assertThat(falha.getExitCode()).isNotZero();
		ExecResult aposFalha = executor.execInContainer("sh", "-c", pasta + "verificar-backup.sh");
		assertThat(aposFalha.getExitCode()).isNotZero();
		assertThat(aposFalha.getStdout()).contains("a última execução falhou (backup incremental saiu com o código");

		rodar("sleep 1; " + pasta + "backup-incremental.sh");
		assertThat(rodar(pasta + "verificar-backup.sh").getStdout()).contains("ok");
		ExecResult pulado = executor.execInContainer("sh", "-c", "(flock 9; sleep 5) 9>/backups-alerta/.trava & sleep 1; "
				+ pasta + "backup-incremental.sh; " + pasta + "verificar-backup.sh");
		assertThat(pulado.getStderr()).contains("outro backup está em andamento");
		assertThat(pulado.getStdout()).contains("ALERTA", "a última execução falhou");

		rodar("sleep 5; " + pasta + "backup-incremental.sh");
		rodar("echo '1 backup completo' > /backups-alerta/estado/ultimo-sucesso");
		ExecResult atrasado = executor.execInContainer("sh", "-c", pasta + "verificar-backup.sh");
		assertThat(atrasado.getExitCode()).isNotZero();
		assertThat(atrasado.getStdout()).contains("último backup concluído há mais de 26h");

		assertThat(DockerClientFactory.instance().client().inspectImageCmd(BancoDoSigh.imagem().get()).exec()
				.getConfig().getHealthcheck().getTest()).contains("/opt/sigh-backup/verificar-backup.sh");
	}

	@Test
	void alertaAcusaArquivamentoDeWalFalhando() throws Exception {
		assertThat(banco.execInContainer("verificar-backup.sh").getStdout()).contains("ok");
		banco.execInContainer("chmod", "000", "/wal");
		try {
			escreverNoWal();
			jdbc.queryForObject("select pg_walfile_name(pg_switch_wal())", String.class);
			esperar(() -> banco.execInContainer("verificar-backup.sh").getExitCode() != 0);
			assertThat(banco.execInContainer("verificar-backup.sh").getStdout()).contains("o arquivamento de WAL está falhando");
		} finally {
			banco.execInContainer("chmod", "700", "/wal");
		}
		jdbc.queryForObject("select pg_reload_conf()", Boolean.class);
		esperar(() -> banco.execInContainer("verificar-backup.sh").getExitCode() == 0);
	}

	@Test
	void arquivamentoERecuperacaoDeUmSegmentoDeWal() throws Exception {
		String ambiente = "SIGH_WAL_DIR=/tmp/wal-teste ";
		String segmento = "0000000100000000000000F9";
		rodar("rm -rf /tmp/wal-teste && mkdir -p /tmp/wal-teste && head -c 200000 /dev/urandom > /tmp/seg-a"
				+ " && head -c 200000 /dev/urandom > /tmp/seg-b && echo resto > /tmp/wal-teste/" + segmento + ".gpg.parcial");

		rodar(ambiente + "arquivar-wal.sh /tmp/seg-a " + segmento);
		assertThat(rodar("ls /tmp/wal-teste").getStdout()).contains(segmento + ".gpg").doesNotContain(".parcial");
		rodar(ambiente + "arquivar-wal.sh /tmp/seg-a " + segmento);
		ExecResult outro = executor.execInContainer("sh", "-c", ambiente + "arquivar-wal.sh /tmp/seg-b " + segmento);
		assertThat(outro.getExitCode()).isNotZero();
		assertThat(outro.getStderr()).contains("já arquivado com outro conteúdo");

		rodar("recuperar-wal.sh /tmp/wal-teste " + segmento + " /tmp/recuperado && cmp /tmp/recuperado /tmp/seg-a");
		assertThat(executor.execInContainer("sh", "-c", "recuperar-wal.sh /tmp/wal-teste 0000000100000000000000FA /tmp/x")
				.getExitCode()).as("segmento ausente: fim do WAL").isEqualTo(1);
		rodar("printf 'x' | dd of=/tmp/wal-teste/" + segmento + ".gpg bs=1 seek=300 conv=notrunc");
		ExecResult alterado = executor.execInContainer("sh", "-c", "recuperar-wal.sh /tmp/wal-teste " + segmento + " /tmp/y");
		assertThat(alterado.getExitCode()).as("segmento alterado: a restauração para").isEqualTo(126);
		assertThat(alterado.getStderr()).contains("restauração interrompida");

		ExecResult semChave = executor.execInContainer("sh", "-c",
				"env -u SIGH_CHAVE_BACKUP recuperar-wal.sh /tmp/wal-teste 0000000100000000000000F9 /tmp/z");
		assertThat(semChave.getExitCode()).as("sem a chave").isEqualTo(126);
		assertThat(executor.execInContainer("sh", "-c", "recuperar-wal.sh /tmp/nao-existe 0000000100000000000000F9 /tmp/z")
				.getExitCode()).as("pasta inacessível").isEqualTo(126);

		ExecResult aspas = executor.execInContainer("sh", "-c", "restaurar.sh /backups /tmp/destino-aspas \"/tmp/wa'l\"");
		assertThat(aspas.getExitCode()).isNotZero();
		assertThat(aspas.getStderr()).contains("não pode ter aspas");
	}

	@Test
	void imagemSobeOAgendadorComCompletoSemanalEIncrementalDiario() throws Exception {
		try (GenericContainer<?> agendador = novoExecutor()) {
			agendador.start();
			ExecResult agenda = agendador.execInContainer("cat", "/etc/crontabs/root");
			assertThat(agenda.getStdout())
					.contains("0 2 * * 0   . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-completo.sh >> /proc/1/fd/1 2>&1")
					.contains("0 2 * * 1-6 . /etc/sigh-backup/ambiente && /opt/sigh-backup/backup-incremental.sh >> /proc/1/fd/1 2>&1");
			assertThat(agendador.execInContainer("pgrep", "crond").getExitCode()).isZero();
			assertThat(agendador.execInContainer("stat", "-c", "%a", "/etc/sigh-backup/ambiente").getStdout().strip())
					.isEqualTo("600");
			assertThat(agendador.execInContainer("sh", "-c", ". /etc/sigh-backup/ambiente && echo $PGHOST $PGSSLMODE")
					.getStdout().strip()).isEqualTo("banco verify-full");
			ExecResult saude = agendador.execInContainer("sh", "-c",
					"for i in $(seq 60); do verificar-backup.sh && exit 0; sleep 2; done; exit 1");
			assertThat(saude.getExitCode()).as(saude.getStdout()).isZero();
			assertThat(agendador.execInContainer("cat", "/backups/cadeia-atual").getExitCode()).isZero();
		}
	}

	private static GenericContainer<?> novoExecutor() {
		return new GenericContainer<>(BancoDoSigh.imagem())
				.withNetwork(rede)
				.withEnv("PGHOST", "banco").withEnv("PGPORT", "5432")
				.withEnv("PGUSER", BancoDoSigh.USUARIO).withEnv("PGPASSWORD", BancoDoSigh.SENHA)
				.withEnv("PGSSLMODE", "verify-full").withEnv("PGSSLROOTCERT", "/certs/autoridade.crt")
				.withEnv("SIGH_BACKUP_DIR", "/backups").withEnv("SIGH_BACKUP_DESTINO_EXTERNO", "/externo")
				.withEnv("SIGH_CHAVE_BACKUP", CHAVE_BACKUP).withEnv("SIGH_WAL_DIR", "/wal")
				.withCopyFileToContainer(MountableFile.forHostPath(certificado.certificadoPem(), 0644), "/certs/autoridade.crt")
				.withCreateContainerCmdModifier(comando -> comando.getHostConfig()
						.withBinds(new Bind(VOLUME_WAL, new Volume("/wal"))));
	}

	private static boolean walDecifradoContem(String texto) throws Exception {
		rodar("rm -rf /tmp/wal-decifrado && mkdir /tmp/wal-decifrado && printf '%s' \"$SIGH_CHAVE_BACKUP\" > /tmp/k"
				+ " && for f in /wal/*.gpg; do gpg --batch --quiet --pinentry-mode loopback --passphrase-file /tmp/k"
				+ " --decrypt \"$f\" | gzip -dc > /tmp/wal-decifrado/$(basename \"$f\" .gpg); done; rm -f /tmp/k");
		return encontra(texto, "/tmp/wal-decifrado");
	}

	private static void escreverNoWal() {
		jdbc.execute("create table if not exists teste_wal (momento timestamptz)");
		jdbc.update("insert into teste_wal values (now())");
	}

	private static String arquivarWalAtual() throws Exception {
		escreverNoWal();
		String segmento = jdbc.queryForObject("select pg_walfile_name(pg_switch_wal())", String.class);
		esperar(() -> rodar("ls /wal").getStdout().contains(segmento + ".gpg"));
		return segmento;
	}

	private static void subirRestaurado(String pasta, int porta) throws Exception {
		rodar("chown -R postgres:postgres " + pasta + " && chmod 700 " + pasta
				+ " && su postgres -s /bin/sh -c \"pg_ctl -D " + pasta + " -l /tmp/restaurado-" + porta + ".log"
				+ " -o '-p " + porta + " -c listen_addresses=127.0.0.1' start\" </dev/null >/dev/null 2>&1");
		esperar(() -> executor.execInContainer("sh", "-c", "PGSSLMODE=disable psql -h 127.0.0.1 -p " + porta + " -d "
				+ BancoDoSigh.BANCO + " -Atc 'select not pg_is_in_recovery()'").getStdout().strip().equals("t"));
	}

	private static void decifrarParte(String cadeia, String parte, String destino) throws Exception {
		rodar("mkdir -p " + destino + " && printf '%s' \"$SIGH_CHAVE_BACKUP\" > /tmp/k && gpg --batch --quiet"
				+ " --pinentry-mode loopback --passphrase-file /tmp/k --decrypt /backups/" + cadeia + "/" + parte
				+ ".tar.gz.gpg | gzip -dc | tar -C " + destino + " -xf - && rm -f /tmp/k");
	}

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

	private static String psql(int porta, String consulta) throws Exception {
		return rodar("PGSSLMODE=disable psql -h 127.0.0.1 -p " + porta + " -d " + BancoDoSigh.BANCO + " -Atc \"" + consulta
				+ "\"").getStdout().strip();
	}

	@FunctionalInterface
	private interface Condicao {
		boolean valida() throws Exception;
	}

	private static void esperar(Condicao condicao) throws Exception {
		for (int tentativa = 0; tentativa < 120; tentativa++) {
			if (condicao.valida()) {
				return;
			}
			Thread.sleep(500);
		}
		throw new AssertionError("A condição não foi atingida em 60 s.");
	}

	private static void inserirPaciente(int numero, boolean cifrado) {
		UUID paciente = UUID.randomUUID();
		UUID prontuario = UUID.randomUUID();
		UUID registro = UUID.randomUUID();
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
				""", registro, prontuario, LocalDateTime.of(2026, 11, 1, 9, 0),
				cifrado ? CIFRA.cifrar(conteudo, "registro_clinico.conteudo", registro) : conteudo,
				cifrado ? CIFRA.cifrar(cid, "registro_clinico.codigo_cid10", registro) : cid, autorId, instituicaoId);
	}

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
		return psql(portaRestaurada, consulta.toString());
	}
}
