package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Liga a conta de acesso (perfil Paciente) ao cadastro do paciente, feito pelo Administrativo. */
public record VinculoContaRequest(@NotNull UUID instituicaoId, @NotBlank @Size(max = 100) String login) {
}
