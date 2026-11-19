package com.sigh.sigh.project.comum.erro;

import java.io.Serial;

/** Requisição bem formada que viola uma regra de negócio (HTTP 422). */
public class RegraNegocioException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}
}
