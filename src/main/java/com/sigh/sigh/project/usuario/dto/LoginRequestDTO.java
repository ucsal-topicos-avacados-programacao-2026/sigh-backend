package com.sigh.sigh.project.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(@NotBlank String login, @NotBlank String senha) {
}
