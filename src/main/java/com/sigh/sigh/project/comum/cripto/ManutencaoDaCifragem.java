package com.sigh.sigh.project.comum.cripto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ManutencaoDaCifragem implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ManutencaoDaCifragem.class);
	private static final int LOTE = 500;
	private static final long TRAVA = 4_242_022L;
	private static final int TENTATIVAS_DE_VACUUM = 5;
	private static final String TEMPO_DE_LOCK_ESGOTADO = "55P03";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transacao;
	private final CifraDeDadosClinicos cifra;
	private final Clock clock;
	private volatile Map<String, Integer> foraDoFormatoPorColuna = Map.of();

	public record Resultado(int cifrados, int regravados, int foraDoFormato) {
	}

	private record Lote(int regravados, int lidos, UUID ultimo) {
	}

	@FunctionalInterface
	private interface Conversao {
		String converter(String valor, CamposCifrados.Campo campo, UUID linha);
	}

	public ManutencaoDaCifragem(JdbcTemplate jdbc, TransactionTemplate transacao, CifraDeDadosClinicos cifra, Clock clock) {
		this.jdbc = jdbc;
		this.transacao = transacao;
		this.cifra = cifra;
		this.clock = clock;
	}

	public Map<String, Integer> foraDoFormatoPorColuna() {
		return foraDoFormatoPorColuna;
	}

	@Override
	public void afterSingletonsInstantiated() {
		executar();
	}

	public Resultado executar() {
		return jdbc.execute((ConnectionCallback<Resultado>) conexao -> {
			try (Statement trava = conexao.createStatement()) {
				trava.execute("select pg_advisory_lock(" + TRAVA + ")");
				try {
					return executarComTrava();
				} finally {
					trava.execute("select pg_advisory_unlock(" + TRAVA + ")");
				}
			}
		});
	}

	private Resultado executarComTrava() {
		refazerVacuumPendente();
		boolean migrado = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from controle_cifragem)", Boolean.class));
		int cifrados = 0;
		int regravados = 0;
		int foraDoFormato = 0;
		for (CamposCifrados.Campo campo : regravaveis()) {
			if (!migrado) {
				cifrados += percorrer(campo, "not starts_with(" + campo.coluna() + ", ?)", CifraDeDadosClinicos.PREFIXO,
						this::migrar);
			}
			regravados += percorrer(campo, "starts_with(" + campo.coluna() + ", '" + CifraDeDadosClinicos.PREFIXO
					+ "') and not starts_with(" + campo.coluna() + ", ?)", cifra.prefixoDaChaveAtual(), this::regravar);
		}
		Map<String, Integer> porColuna = new LinkedHashMap<>();
		for (CamposCifrados.Campo campo : CamposCifrados.daAplicacao()) {
			int fora = conferir(campo);
			foraDoFormato += fora;
			if (fora > 0) {
				porColuna.put(campo.nome(), fora);
			}
		}
		foraDoFormatoPorColuna = Map.copyOf(porColuna);
		if (!migrado) {
			jdbc.update("insert into controle_cifragem (id, formato, concluida_em) values (1, 'v2', ?) on conflict do nothing",
					LocalDateTime.now(clock));
		}
		refazerVacuumPendente();
		if (cifrados > 0 || regravados > 0) {
			log.info("Cifragem: {} valor(es) legado(s) cifrado(s) e {} regravado(s) com a chave atual.", cifrados, regravados);
		}
		return new Resultado(cifrados, regravados, foraDoFormato);
	}

	private static List<CamposCifrados.Campo> regravaveis() {
		return CamposCifrados.daAplicacao().stream().filter(campo -> !campo.legadoImutavel()).toList();
	}

	private String migrar(String valor, CamposCifrados.Campo campo, UUID linha) {
		try {
			return cifra.cifrar(cifra.decifrarLegado(valor, campo.nome(), linha), campo.nome(), linha);
		} catch (DadoClinicoIlegivelException e) {
			log.error("Migração recusada: {}", e.getMessage());
			return null;
		}
	}

	private String regravar(String valor, CamposCifrados.Campo campo, UUID linha) {
		try {
			return cifra.cifrar(cifra.decifrar(valor, campo.nome(), linha), campo.nome(), linha);
		} catch (DadoClinicoIlegivelException e) {
			log.error("Troca de chave recusada: {}", e.getMessage());
			return null;
		}
	}

	private int percorrer(CamposCifrados.Campo campo, String condicao, String parametro, Conversao conversao) {
		int regravados = 0;
		UUID ultimo = null;
		while (true) {
			UUID depoisDe = ultimo;
			Lote lote = transacao.execute(status -> {
				String filtro = " where " + campo.coluna() + " is not null and " + condicao
						+ (depoisDe == null ? "" : " and id > ?");
				List<Map<String, Object>> linhas = depoisDe == null
						? jdbc.queryForList(selecao(campo, filtro), parametro)
						: jdbc.queryForList(selecao(campo, filtro), parametro, depoisDe);
				int feitos = 0;
				UUID fim = null;
				for (Map<String, Object> linha : linhas) {
					fim = (UUID) linha.get("id");
					String novo = conversao.converter((String) linha.get("valor"), campo, fim);
					if (novo != null) {
						jdbc.update("update " + campo.tabela() + " set " + campo.coluna() + " = ? where id = ?", novo, fim);
						feitos++;
					}
				}
				if (feitos > 0) {
					jdbc.update("insert into cifragem_vacuum_pendente (tabela) values (?) on conflict do nothing", campo.tabela());
				}
				return new Lote(feitos, linhas.size(), fim);
			});
			regravados += lote.regravados();
			if (lote.lidos() < LOTE) {
				return regravados;
			}
			ultimo = lote.ultimo();
		}
	}

	private static String selecao(CamposCifrados.Campo campo, String filtro) {
		return "select id, " + campo.coluna() + " as valor from " + campo.tabela() + filtro + " order by id limit " + LOTE
				+ " for update";
	}

	private int conferir(CamposCifrados.Campo campo) {
		String coluna = "t." + campo.coluna();
		String foraDaLista = campo.legadoImutavel()
				? " and not exists (select 1 from " + campo.legadoListadoEm() + " l where l.registro_id = t.id)" : "";
		Map<String, Object> contagem = jdbc.queryForMap("select count(*) filter (where not starts_with(" + coluna + ", ?)"
				+ foraDaLista + ") as fora, count(*) filter (where starts_with(" + coluna + ", ?) and not starts_with("
				+ coluna + ", ?)) as antiga from " + campo.tabela() + " t where " + coluna + " is not null",
				CifraDeDadosClinicos.PREFIXO, CifraDeDadosClinicos.PREFIXO, cifra.prefixoDaChaveAtual());
		int fora = ((Number) contagem.get("fora")).intValue();
		int antiga = campo.legadoImutavel() ? 0 : ((Number) contagem.get("antiga")).intValue();
		if (fora > 0) {
			log.error("{} valor(es) sem a cifra atual em {}: gravados fora da aplicação depois da migração. A leitura"
					+ " deles é recusada.", fora, campo.nome());
		}
		if (antiga > 0) {
			log.error("{} valor(es) de chave anterior em {} não puderam ser regravados.", antiga, campo.nome());
		}
		return fora;
	}

	private void refazerVacuumPendente() {
		Set<String> conhecidas = regravaveis().stream().map(CamposCifrados.Campo::tabela).collect(Collectors.toSet());
		for (String tabela : jdbc.queryForList("select tabela from cifragem_vacuum_pendente order by tabela", String.class)) {
			if (!conhecidas.contains(tabela)) {
				log.error("Tabela sem coluna cifrada na fila de VACUUM: ignorada.");
				jdbc.update("delete from cifragem_vacuum_pendente where tabela = ?", tabela);
				continue;
			}
			if (!Boolean.TRUE.equals(jdbc.queryForObject("select pg_get_userbyid(relowner) = current_user from pg_class"
					+ " where oid = to_regclass(?)", Boolean.class, tabela))) {
				log.error("VACUUM FULL de {} não feito: o usuário do banco não é dono da tabela. Fica na fila.", tabela);
				continue;
			}
			if (!Boolean.TRUE.equals(jdbc.queryForObject(
					"select has_table_privilege('pg_catalog.pg_statistic', 'MAINTAIN')", Boolean.class))) {
				log.error("VACUUM FULL de {} não feito: sem permissão para limpar as estatísticas (pg_statistic), que"
						+ " guardam amostras dos valores antigos. Conceda: GRANT pg_maintain TO <usuário da aplicação>."
						+ " Fica na fila.", tabela);
				continue;
			}
			if (!semRetratoAntigo()) {
				log.warn("VACUUM FULL de {} adiado: outra sessão segura um retrato antigo do banco. Fica na fila.", tabela);
				continue;
			}
			if (vacuumFull(tabela)) {
				jdbc.update("delete from cifragem_vacuum_pendente where tabela = ?", tabela);
			} else {
				log.warn("VACUUM FULL de {} adiado: a tabela está em uso. Fica na fila.", tabela);
			}
		}
	}

	private boolean semRetratoAntigo() {
		for (int tentativa = 0; tentativa < TENTATIVAS_DE_VACUUM; tentativa++) {
			Integer outras = jdbc.queryForObject("""
					select (select count(*) from pg_stat_activity where backend_type = 'client backend'
					  and backend_xmin is not null and pid <> pg_backend_pid() and datname = current_database())
					+ (select count(*) from pg_replication_slots where xmin is not null or catalog_xmin is not null)
					""", Integer.class);
			if (outras == null || outras == 0) {
				return true;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private boolean vacuumFull(String tabela) {
		return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) conexao -> {
			try (Statement comando = conexao.createStatement()) {
				comando.execute("set lock_timeout = '30s'");
				try {
					comando.execute("vacuum (full, analyze) " + tabela);
					comando.execute("vacuum full pg_statistic");
					return true;
				} catch (SQLException e) {
					if (TEMPO_DE_LOCK_ESGOTADO.equals(e.getSQLState())) {
						return false;
					}
					throw e;
				} finally {
					comando.execute("reset lock_timeout");
				}
			}
		}));
	}
}
