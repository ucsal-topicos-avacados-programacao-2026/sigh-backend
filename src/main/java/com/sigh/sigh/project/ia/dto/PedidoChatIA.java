package com.sigh.sigh.project.ia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoChatIA(
		String model,
		List<MensagemChatIA> messages,
		double temperature,
		@JsonProperty("reasoning_effort") String reasoningEffort,
		@JsonProperty("response_format") FormatoRespostaIA responseFormat) {
}
