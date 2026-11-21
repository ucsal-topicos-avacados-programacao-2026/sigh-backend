package com.sigh.sigh.project.comum.erro;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class TratadorDeErrosTest {

	private final TratadorDeErros tratador = new TratadorDeErros();

	@Test
	void violacaoDeUnicidadeViraConflito409SemDetalhesDoBanco() {
		ProblemDetail problema = tratador.integridade(erro("23505", "Key (cpf)=(52998224725) already exists"));

		assertThat(problema.getStatus()).isEqualTo(409);
		assertThat(problema.getDetail()).doesNotContain("52998224725").doesNotContain("cpf");
	}

	@Test
	void outrasViolacoesDeIntegridadeViramErroInterno500() {
		for (String sqlState : new String[] {"23503", "23502", "22001"}) {
			ProblemDetail problema = tratador.integridade(erro(sqlState, "detalhe interno do banco"));

			assertThat(problema.getStatus()).isEqualTo(500);
			assertThat(problema.getDetail()).isEqualTo("Erro interno.");
		}
	}

	@Test
	void violacaoSemSQLExceptionNaCadeiaViraErroInterno500() {
		ProblemDetail problema = tratador.integridade(new DataIntegrityViolationException("sem causa"));

		assertThat(problema.getStatus()).isEqualTo(500);
		assertThat(ViolacaoDeUnicidade.sqlState(new DataIntegrityViolationException("sem causa"))).isNull();
	}

	private static DataIntegrityViolationException erro(String sqlState, String mensagem) {
		return new DataIntegrityViolationException("falha",
				new RuntimeException("envoltorio", new SQLException(mensagem, sqlState)));
	}
}
