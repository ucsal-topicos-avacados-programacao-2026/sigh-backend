package com.sigh.sigh.project.comum.erro;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

public final class ViolacaoDeUnicidade {

	private static final String SQLSTATE_UNIQUE = "23505";

	private ViolacaoDeUnicidade() {
	}

	public static boolean eh(Throwable erro) {
		return SQLSTATE_UNIQUE.equals(sqlState(erro));
	}

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
