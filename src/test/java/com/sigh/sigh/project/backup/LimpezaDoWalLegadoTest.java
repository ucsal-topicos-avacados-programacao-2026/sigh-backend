package com.sigh.sigh.project.backup;

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
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-26 (RNF-004): o dado clínico que entrou em claro antes da cifragem fica nos segmentos de WAL do servidor,
 * inclusive em segmentos antigos reaproveitados para uso futuro (achado do TST-04). Depois do
 * limpar-wal-legado.sh, ele não existe mais em nenhum arquivo de pg_wal. Usa a mesma imagem e o mesmo
 * script de produção.
 */
class LimpezaDoWalLegadoTest {

	private static final String PG_WAL = "/var/lib/postgresql/data/pg_wal";
	private static final String CHAVE_BACKUP = "chave-ficticia-das-copias-de-teste-com-32+";
	private static final String MARCADOR_CLINICO = "MarcadorClinicoLegadoNoWal";
	private static final CifraDeDadosClinicos CIFRA = new CifraDeDadosClinicos(
			Base64.getEncoder().encodeToString("chave-ficticia-dos-dados-32bytes".getBytes()), "1", "");

	private static CertificadoDeTeste certificado;
	private static GenericContainer<?> banco;
	private static JdbcTemplate jdbc;
	private static ManutencaoDaCifragem manutencao;

	@BeforeAll
	static void subirBanco() throws Exception {
		certificado = BancoDoSigh.certificado();
		banco = BancoDoSigh.comTls(certificado).withEnv("SIGH_WAL_DIR", "/wal").withEnv("SIGH_CHAVE_BACKUP", CHAVE_BACKUP);
		banco.start();
		DriverManagerDataSource fonte = new DriverManagerDataSource(BancoDoSigh.urlComTls(banco, certificado),
				BancoDoSigh.USUARIO, BancoDoSigh.SENHA);
		jdbc = new JdbcTemplate(fonte);
		Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate();
		manutencao = new ManutencaoDaCifragem(jdbc, new TransactionTemplate(new DataSourceTransactionManager(fonte)),
				CIFRA, Clock.systemUTC());
	}

	@AfterAll
	static void derrubar() {
		if (banco != null) {
			banco.stop();
		}
	}

	@Test
	void dadoLegadoSaiDeTodosOsSegmentosDoServidorInclusiveDosReaproveitados() throws Exception {
		assertThat(jdbc.queryForObject("show wal_recycle", String.class)).as("a imagem sobe sem reaproveitar WAL")
				.isEqualTo("off");
		// Com o WAL comprimido, a busca do texto não o acharia mesmo presente: o controle negativo não valeria.
		assertThat(jdbc.queryForObject("show wal_compression", String.class)).isEqualTo("off");
		UUID registro = inserirRegistroLegado();
		assertThat(manutencao.executar().cifrados()).as("a migração cifra o dado legado").isEqualTo(2);
		assertThat(noWal(MARCADOR_CLINICO)).as("controle: o WAL da migração tem o dado em claro").isTrue();
		// O cenário do TST-04: um segmento antigo, com o dado, renomeado para uso futuro (como faz o wal_recycle).
		String segmentoComODado = rodar("LC_ALL=C grep -r -a -l -F '" + MARCADOR_CLINICO + "' " + PG_WAL + " | head -1")
				.getStdout().strip();
		// Dezesseis adiante: mais longe do que as trocas de segmento da limpeza alcançam sem um enchimento que chegue
		// até ele (e mais do que a quantidade de segmentos que existiam).
		String futuro = segmentoFuturo(jdbc.queryForObject("select pg_walfile_name(pg_current_wal_lsn())", String.class), 16);
		rodar("cp " + segmentoComODado + " " + PG_WAL + "/" + futuro + " && chown postgres:postgres " + PG_WAL + "/" + futuro);

		ExecResult limpeza = banco.execInContainer("limpar-wal-legado.sh");

		assertThat(limpeza.getExitCode()).as(limpeza.getStderr()).isZero();
		assertThat(limpeza.getStderr()).contains("limpeza concluída");
		assertThat(noWal(MARCADOR_CLINICO)).as("nenhum segmento do servidor tem mais o dado legado").isFalse();
		jdbc.update("insert into instituicao (id, nome, tipo, credenciada) values (?, 'MarcadorDepoisDaLimpeza', 'PUBLICA', true)",
				UUID.randomUUID());
		assertThat(noWal("MarcadorDepoisDaLimpeza")).as("controle: a busca acha o que foi gravado depois").isTrue();
		String gravado = jdbc.queryForObject("select conteudo from registro_clinico where id = ?", String.class, registro);
		assertThat(CIFRA.decifrar(gravado, "registro_clinico.conteudo", registro))
				.as("o dado continua no banco, cifrado").contains(MARCADOR_CLINICO);
	}

	@Test
	void arquivamentoParadoFazALimpezaFalharDizendoOMotivo() throws Exception {
		// O arquivamento roda como postgres: sem permissão na pasta, ele falha e o servidor não apaga o segmento.
		rodar("chmod 000 /wal");
		try {
			ExecResult limpeza = banco.execInContainer("sh", "-c", "SIGH_LIMPEZA_ESPERA=5 limpar-wal-legado.sh");

			assertThat(limpeza.getExitCode()).isNotZero();
			assertThat(limpeza.getStderr()).contains("arquivamento do WAL está parado");
		} finally {
			rodar("chmod 700 /wal");
		}
	}

	@Test
	void slotDeReplicacaoQueRetemOWalFazALimpezaFalharDizendoOMotivo() throws Exception {
		jdbc.queryForObject("select 1 from pg_create_physical_replication_slot('sigh_teste_retencao', true)", Integer.class);
		try {
			ExecResult limpeza = banco.execInContainer("limpar-wal-legado.sh");

			assertThat(limpeza.getExitCode()).isNotZero();
			assertThat(limpeza.getStderr()).contains("continuam no servidor").contains("slots de replicação=1");
		} finally {
			jdbc.queryForObject("select 1 from pg_drop_replication_slot('sigh_teste_retencao')", Integer.class);
		}
	}

	/** Paciente com um diagnóstico gravado em claro, como um dado de antes da cifragem. */
	private static UUID inserirRegistroLegado() {
		UUID instituicao = UUID.randomUUID();
		UUID autor = UUID.randomUUID();
		UUID paciente = UUID.randomUUID();
		UUID prontuario = UUID.randomUUID();
		UUID registro = UUID.randomUUID();
		jdbc.update("insert into instituicao (id, nome, tipo, credenciada) values (?, 'Hospital Fictício', 'PUBLICA', true)",
				instituicao);
		jdbc.update("insert into usuario (id, login, nome, senha_hash, mfa_habilitado) values (?, ?, 'Médico Fictício',"
				+ " 'hash', false)", autor, "medico." + autor);
		jdbc.update("insert into paciente (id, cpf, nome, data_nascimento) values (?, '90000000001', 'Paciente Fictício', ?)",
				paciente, LocalDate.of(1980, 1, 1));
		jdbc.update("insert into prontuario (id, paciente_id, criado_em) values (?, ?, ?)", prontuario, paciente,
				LocalDateTime.of(2026, 11, 1, 8, 0));
		jdbc.update("""
				insert into registro_clinico (id, prontuario_id, tipo, criado_em, conteudo, categoria_informacao,
				  codigo_cid10, autor_id, origem_id) values (?, ?, 'DIAGNOSTICO', ?, ?, 'Saúde mental', 'F32.1', ?, ?)
				""", registro, prontuario, LocalDateTime.of(2026, 11, 1, 9, 0), MARCADOR_CLINICO + " episódio depressivo",
				autor, instituicao);
		return registro;
	}

	/** O nome do segmento {@code adiante} posições depois, na mesma linha do tempo (segmentos de 16 MB). */
	static String segmentoFuturo(String atual, int adiante) {
		long posicao = Long.parseLong(atual.substring(8, 16), 16) * 256 + Long.parseLong(atual.substring(16, 24), 16)
				+ adiante;
		return atual.substring(0, 8) + String.format("%08X%08X", posicao / 256, posicao % 256);
	}

	/** Busca em bytes (LC_ALL=C), em todos os arquivos de pg_wal. */
	private static boolean noWal(String texto) throws Exception {
		ExecResult busca = banco.execInContainer("sh", "-c", "LC_ALL=C grep -r -a -l -F '" + texto + "' " + PG_WAL);
		assertThat(busca.getExitCode()).as(busca.getStderr()).isIn(0, 1);
		return busca.getExitCode() == 0;
	}

	private static ExecResult rodar(String comando) throws Exception {
		ExecResult resultado = banco.execInContainer("sh", "-c", comando);
		assertThat(resultado.getExitCode()).as(comando + "\n" + resultado.getStderr()).isZero();
		return resultado;
	}
}
