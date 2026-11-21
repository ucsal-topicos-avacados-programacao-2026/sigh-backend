package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;

public record DecisaoPacienteRequest(@NotNull(message = "Decisão é obrigatória") DecisaoPaciente decisao) {

	public enum DecisaoPaciente {
		AUTORIZADO,
		REVOGADO
	}
}
