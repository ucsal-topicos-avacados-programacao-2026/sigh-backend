package com.sigh.sigh.project.ia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FormatoRespostaIA(String type, @JsonProperty("json_schema") EsquemaRespostaIA jsonSchema) {

	private static final String TIPO_ESQUEMA_JSON = "json_schema";

	public static FormatoRespostaIA classificacaoDeRisco() {
		return new FormatoRespostaIA(TIPO_ESQUEMA_JSON, EsquemaRespostaIA.classificacaoDeRisco());
	}
}
