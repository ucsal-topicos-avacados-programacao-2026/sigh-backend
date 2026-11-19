package com.sigh.sigh.project.ia.dto;

public record MensagemChatIA(String role, String content) {

	private static final String PAPEL_SISTEMA = "system";
	private static final String PAPEL_USUARIO = "user";

	public static MensagemChatIA doSistema(String conteudo) {
		return new MensagemChatIA(PAPEL_SISTEMA, conteudo);
	}

	public static MensagemChatIA doUsuario(String conteudo) {
		return new MensagemChatIA(PAPEL_USUARIO, conteudo);
	}
}
