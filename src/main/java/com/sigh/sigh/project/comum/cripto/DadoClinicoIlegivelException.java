package com.sigh.sigh.project.comum.cripto;

import java.util.UUID;

public class DadoClinicoIlegivelException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String coluna;
	private final UUID linha;

	public DadoClinicoIlegivelException(String coluna, UUID linha, String motivo) {
		super("Dado clínico ilegível em " + coluna + " (linha " + linha + "): " + motivo + ".");
		this.coluna = coluna;
		this.linha = linha;
	}

	public String getColuna() {
		return coluna;
	}

	public UUID getLinha() {
		return linha;
	}
}
