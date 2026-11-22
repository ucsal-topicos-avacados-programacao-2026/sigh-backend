package com.sigh.sigh.project.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NovaSenhaRequest(
		@NotBlank(message = "Nova senha é obrigatória")
		@Size(min = 10, max = 72, message = "A senha deve ter de 10 a 72 caracteres") String novaSenha) {
}
