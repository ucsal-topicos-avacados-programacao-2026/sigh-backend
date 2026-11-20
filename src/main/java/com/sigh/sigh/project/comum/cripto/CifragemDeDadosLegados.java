package com.sigh.sigh.project.comum.cripto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Na inicialização, cifra os dados clínicos que ainda estão em claro: os gravados antes da
 * criptografia e os dados de demonstração, que entram por SQL. É idempotente: só toca valores sem o
 * prefixo da cifra. O log informa apenas quantos valores foram cifrados, nunca o conteúdo.
 *
 * <p>A justificativa da auditoria fica de fora: a tabela é imutável (RN-006), então só os registros
 * novos saem cifrados.
 *
 * <p>Depois de cifrar, a tabela passa por VACUUM FULL: o UPDATE deixa a versão antiga da linha, em
 * claro, nos arquivos do banco até ela ser reescrita, e o backup físico copiaria esses arquivos.
 */
@Component
public class CifragemDeDadosLegados implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CifragemDeDadosLegados.class);
	private static final int LOTE = 500;

	/** Tabela e coluna de cada dado clínico cifrado; nomes fixos, nunca vindos de fora. */
	static final List<String[]> COLUNAS = List.of(
			new String[] {"registro_clinico", "conteudo"},
			new String[] {"registro_clinico", "codigo_cid10"},
			new String[] {"atendimento", "motivo"},
			new String[] {"triagem", "sintomas"},
			new String[] {"triagem", "pressao_arterial"},
			new String[] {"triagem", "frequencia_cardiaca"},
			new String[] {"triagem", "frequencia_respiratoria"},
			new String[] {"triagem", "temperatura"},
			new String[] {"triagem", "saturacao_oxigenio"},
			new String[] {"resumo_historico_ia", "texto"},
			new String[] {"analise_auditoria", "descricao"},
			new String[] {"usuario", "mfa_segredo"});

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transacao;
	private final CifraDeDadosClinicos cifra;

	public CifragemDeDadosLegados(JdbcTemplate jdbc, TransactionTemplate transacao, CifraDeDadosClinicos cifra) {
		this.jdbc = jdbc;
		this.transacao = transacao;
		this.cifra = cifra;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		int cifrados = cifrarLegados();
		if (cifrados > 0) {
			log.info("Dados clínicos legados cifrados: {} valor(es).", cifrados);
		}
	}

	/** Cifra todos os valores em claro das colunas clínicas e devolve quantos foram cifrados. */
	public int cifrarLegados() {
		int total = 0;
		Set<String> tabelasRegravadas = new LinkedHashSet<>();
		for (String[] coluna : COLUNAS) {
			int noLote;
			do {
				noLote = cifrarLote(coluna[0], coluna[1]);
				total += noLote;
				if (noLote > 0) {
					tabelasRegravadas.add(coluna[0]);
				}
			} while (noLote > 0);
		}
		for (String tabela : tabelasRegravadas) {
			jdbc.execute("vacuum full " + tabela);
		}
		return total;
	}

	private int cifrarLote(String tabela, String coluna) {
		Integer cifrados = transacao.execute(status -> {
			List<Map<String, Object>> emClaro = jdbc.queryForList("select id, " + coluna + " as valor from " + tabela
					+ " where " + coluna + " is not null and " + coluna + " not like '" + CifraDeDadosClinicos.PREFIXO
					+ "%' limit " + LOTE + " for update skip locked");
			for (Map<String, Object> linha : emClaro) {
				jdbc.update("update " + tabela + " set " + coluna + " = ? where id = ?",
						cifra.cifrar(linha.get("valor").toString()), (UUID) linha.get("id"));
			}
			return emClaro.size();
		});
		return cifrados == null ? 0 : cifrados;
	}
}
