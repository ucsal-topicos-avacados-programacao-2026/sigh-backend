package com.sigh.sigh.project.paciente.dto;

import java.util.UUID;

/** Paciente cujos dados o usuário pode gerenciar: o próprio cadastro ou um representado. */
public record PacienteGerenciadoResponse(UUID pacienteId, String nome, Relacao relacao) {

	public enum Relacao {
		TITULAR,
		REPRESENTANTE_LEGAL
	}
}
