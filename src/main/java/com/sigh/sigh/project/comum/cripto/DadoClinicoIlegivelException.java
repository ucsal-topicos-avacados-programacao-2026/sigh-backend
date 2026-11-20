package com.sigh.sigh.project.comum.cripto;

import java.util.UUID;

/**
 * Valor de coluna cifrada que não pode ser lido: em claro, no formato antigo, adulterado, copiado de
 * outra linha ou coluna, ou de chave desconhecida. A mensagem diz onde (coluna e id da linha) e por
 * quê, nunca o conteúdo. Não estende {@link IllegalStateException} para o Spring não a converter em
 * erro de uso da API de dados.
 */
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
