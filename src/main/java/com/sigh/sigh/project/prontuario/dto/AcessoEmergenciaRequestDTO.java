package com.sigh.sigh.project.prontuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcessoEmergenciaRequestDTO(
		@NotBlank @Size(min = 20, max = 2000, message = "descreva a emergência com 20 a 2000 caracteres")
		String justificativa) {
}
