package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AutorizacaoAdicionalRequest(@NotNull UUID instituicaoId, @Size(max = 50) String categoria,
		UUID registroId) {
}
