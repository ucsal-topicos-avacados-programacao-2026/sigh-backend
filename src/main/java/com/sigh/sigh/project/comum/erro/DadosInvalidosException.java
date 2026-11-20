package com.sigh.sigh.project.comum.erro;

import java.io.Serial;
import java.util.Map;

/** Campos obrigatórios conforme o contexto da operação (HTTP 400). */
public class DadosInvalidosException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final transient Map<String, String> campos;

	public DadosInvalidosException(Map<String, String> campos) {
		super("Dados inválidos");
		this.campos = Map.copyOf(campos);
	}

	public Map<String, String> getCampos() {
		return campos;
	}
}
