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

/**
 * Manutenção das colunas cifradas, feita direto no banco ao subir a aplicação, antes de o servidor
 * web aceitar requisições (RNF-004):
 * <ol>
 * <li>refaz o VACUUM FULL que ficou pendente se a aplicação caiu no meio da última execução;</li>
 * <li>na primeira execução depois desta versão, cifra no formato atual o que ainda está em claro (dados
 * anteriores à criptografia e os de demonstração, que entram por SQL) ou no formato antigo
 * {@code sigh:v1:}, e marca a migração como concluída;</li>
 * <li>regrava com a chave atual os valores de chaves anteriores, depois de uma troca de chave;</li>
 * <li>depois da migração, não cifra mais valor em claro: conta e registra no log cada coluna com valor
 * fora do formato atual, que só pode ter sido gravado fora da aplicação. A leitura desses valores é
 * recusada ({@link CifragemDasEntidades}).</li>
 * </ol>
 *
 * <p>Toda tabela regravada passa por VACUUM FULL com ANALYZE, seguido de VACUUM FULL das estatísticas
 * (pg_statistic): o UPDATE deixa a versão antiga da linha (em claro ou com a chave antiga) nos arquivos do
 * banco, as estatísticas guardam amostras dos valores, e o backup físico copiaria esses arquivos. A tabela entra
 * na fila de VACUUM na mesma transação que a regrava, então uma queda no meio não deixa passar. O log
 * informa só contagens e colunas, nunca o conteúdo. Uma trava do PostgreSQL impede duas instâncias de
 * rodarem isto ao mesmo tempo.
 */
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

	/** Quantos valores foram cifrados (legado), regravados (chave antiga) e quantos ficaram ilegíveis. */
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

	/** Colunas com valor fora do formato atual na última execução, e quantos (alimenta a saúde da aplicação). */
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

	/** Colunas que podem ser regravadas: todas menos a da auditoria, que é imutável (RN-006). */
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

	/**
	 * Percorre, em lotes e em ordem de id, as linhas da coluna que atendem à condição e regrava as que a
	 * conversão aceitar. As que ela recusa ficam como estão e não voltam no lote seguinte.
	 */
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

	/**
	 * Registra no log o que sobrou fora do formato atual ou com chave antiga, e devolve quantos valores
	 * estão fora do formato. Na coluna imutável da auditoria, só as linhas da lista de legado podem estar
	 * fora; lá a chave antiga não é contada, porque a linha não pode ser regravada.
	 */
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

	/**
	 * VACUUM FULL das tabelas na fila. Só aceita tabelas que têm coluna cifrada: o nome vem do banco e
	 * entra no comando. A tabela só sai da fila quando o VACUUM pôde mesmo limpar:
	 * <ul>
	 * <li>o usuário do banco precisa ser dono dela e poder fazer a manutenção das estatísticas, pg_statistic
	 * (papel pg_maintain); senão o PostgreSQL só avisa e não faz nada;</li>
	 * <li>nenhuma outra sessão pode segurar um retrato antigo do banco (senão as versões antigas que ela
	 * ainda enxerga são copiadas para a tabela nova);</li>
	 * <li>o lock exclusivo tem tempo limite, para não travar a aplicação que estiver atendendo.</li>
	 * </ul>
	 * O que não pôde ser feito fica na fila para a próxima inicialização, com aviso no log.
	 */
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

	/** Espera um pouco por sessões que ainda seguram um retrato antigo (transação aberta, slot de replicação). */
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
					// ANALYZE junto: as estatísticas da coluna (pg_statistic) guardam amostras dos valores, que
					// estavam em claro; o VACUUM FULL do catálogo some com a versão antiga delas.
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
