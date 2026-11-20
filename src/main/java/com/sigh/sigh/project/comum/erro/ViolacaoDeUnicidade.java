package com.sigh.sigh.project.comum.erro;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

/** Identifica violação de UNIQUE no PostgreSQL (SQLState 23505) em qualquer ponto da cadeia de causas. */
public final class ViolacaoDeUnicidade {

	private static final String SQLSTATE_UNIQUE = "23505";

	private ViolacaoDeUnicidade() {
	}

	public static boolean eh(Throwable erro) {
		return SQLSTATE_UNIQUE.equals(sqlState(erro));
	}

	/** Nome da constraint violada informado pelo Hibernate (não contém valores das colunas), ou null. */
	public static String restricao(Throwable erro) {
		for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
			if (atual instanceof ConstraintViolationException violacao) {
				return violacao.getConstraintName();
			}
			if (atual.getCause() == atual) {
				break;
			}
		}
		return null;
	}

	/** SQLState da primeira SQLException da cadeia, ou null se não houver. */
	public static String sqlState(Throwable erro) {
		for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
			if (atual instanceof SQLException sql && sql.getSQLState() != null) {
				return sql.getSQLState();
			}
			if (atual.getCause() == atual) {
				break;
			}
		}
		return null;
	}
}
