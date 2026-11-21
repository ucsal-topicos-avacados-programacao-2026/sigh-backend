package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InstituicaoDoAdministrativoRequest(@NotNull UUID instituicaoId) {
}
