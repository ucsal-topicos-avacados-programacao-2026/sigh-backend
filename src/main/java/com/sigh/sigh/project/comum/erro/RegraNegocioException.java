package com.sigh.sigh.project.comum.erro;

import java.io.Serial;

public class RegraNegocioException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}
}
