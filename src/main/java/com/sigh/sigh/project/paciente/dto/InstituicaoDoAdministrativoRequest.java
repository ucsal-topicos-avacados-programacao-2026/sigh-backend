package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Instituição em que o Administrativo atua na operação. */
public record InstituicaoDoAdministrativoRequest(@NotNull UUID instituicaoId) {
}
