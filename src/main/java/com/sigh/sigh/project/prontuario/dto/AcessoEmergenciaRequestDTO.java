package com.sigh.sigh.project.prontuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Justificativa do acesso de emergência (RN-003), gravada na auditoria antes de exibir os dados. */
public record AcessoEmergenciaRequestDTO(
		@NotBlank @Size(min = 20, max = 2000, message = "descreva a emergência com 20 a 2000 caracteres")
		String justificativa) {
}
