package com.sigh.sigh.project.ia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PedidoChatIA(
		String model,
		List<MensagemChatIA> messages,
		double temperature,
		@JsonProperty("reasoning_effort") String reasoningEffort,
		@JsonProperty("response_format") FormatoRespostaIA responseFormat) {
}
