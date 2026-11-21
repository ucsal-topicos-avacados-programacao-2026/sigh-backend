package com.sigh.sigh.project.ia.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record EsquemaRespostaIA(String name, boolean strict, Map<String, Object> schema) {

	private static final String NOME_CLASSIFICACAO = "classificacao_de_risco";

	public static EsquemaRespostaIA classificacaoDeRisco() {
		List<String> cores = Arrays.stream(CorRisco.values()).map(CorRisco::name).toList();
		Map<String, Object> campoCor = Map.of("type", "string", "enum", cores);
		Map<String, Object> esquema = Map.of(
				"type", "object",
				"properties", Map.of("cor", campoCor),
				"required", List.of("cor"),
				"additionalProperties", false);
		return new EsquemaRespostaIA(NOME_CLASSIFICACAO, true, esquema);
	}
}
