package com.sigh.sigh.project.ia.dto;

import java.util.List;
import java.util.Optional;

public record RespostaChatIA(List<Escolha> choices) {

	public String conteudo() {
		return Optional.ofNullable(choices)
				.filter(escolhas -> !escolhas.isEmpty())
				.map(List::getFirst)
				.map(Escolha::message)
				.map(MensagemChatIA::content)
				.orElse("");
	}

	public record Escolha(MensagemChatIA message) {
	}
}
