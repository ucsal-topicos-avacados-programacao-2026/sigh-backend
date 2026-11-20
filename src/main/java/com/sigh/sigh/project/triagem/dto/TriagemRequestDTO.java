package com.sigh.sigh.project.triagem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TriagemRequestDTO(
		@NotNull UUID atendimentoId,
		@NotBlank String sintomas,
		@NotNull @Valid SinaisVitaisDTO sinaisVitais) {
}
