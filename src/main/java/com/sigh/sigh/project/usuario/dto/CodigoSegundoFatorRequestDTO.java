package com.sigh.sigh.project.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodigoSegundoFatorRequestDTO(
		@NotBlank @Pattern(regexp = "\\d{6}", message = "o código tem 6 dígitos") String codigo) {
}
