package com.sigh.sigh.project.paciente.dto;

import java.util.UUID;

public record PacienteGerenciadoResponse(UUID pacienteId, String nome, Relacao relacao) {

	public enum Relacao {
		TITULAR,
		REPRESENTANTE_LEGAL
	}
}
