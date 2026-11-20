package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Autorização adicional explícita (RF-009): libera para uma instituição uma categoria ou um registro
 * que o paciente restringiu.
 */
public record AutorizacaoAdicionalRequest(@NotNull UUID instituicaoId, @Size(max = 50) String categoria,
		UUID registroId) {
}
