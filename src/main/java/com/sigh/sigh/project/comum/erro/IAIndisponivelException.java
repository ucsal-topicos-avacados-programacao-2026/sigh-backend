package com.sigh.sigh.project.comum.erro;

import java.io.Serial;

public class IAIndisponivelException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public IAIndisponivelException(String mensagem) {
		super(mensagem);
	}
}
