package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de representante legal pela Recepção; sem início, vale a partir de agora. */
public record RepresentacaoRequest(@NotNull UUID instituicaoId, @NotBlank @Size(max = 100) String login,
		@NotBlank @Size(max = 100) String documento, LocalDateTime inicioEm, LocalDateTime fimEm) {
}
