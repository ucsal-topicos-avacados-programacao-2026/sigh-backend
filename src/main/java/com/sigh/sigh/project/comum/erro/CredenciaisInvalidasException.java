package com.sigh.sigh.project.comum.erro;

import java.io.Serial;

public class CredenciaisInvalidasException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public CredenciaisInvalidasException(String mensagem) {
		super(mensagem);
	}
}
