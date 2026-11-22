package com.sigh.sigh.project.usuario.dto;

import jakarta.validation.constraints.NotNull;

public record AtivoRequest(@NotNull(message = "Informe se o usuário fica ativo") Boolean ativo) {
}
