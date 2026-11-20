package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;

public record DecisaoPacienteRequest(@NotNull(message = "Decisão é obrigatória") DecisaoPaciente decisao) {

	/** Decisões que o próprio paciente pode tomar depois do cadastro (RF-002, RN-007). */
	public enum DecisaoPaciente {
		AUTORIZADO,
		REVOGADO
	}
}
