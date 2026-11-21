package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.cpf.CpfValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuscaPacienteRequest(
		@NotBlank(message = "CPF é obrigatório") @CpfValido String cpf,
		@NotNull(message = "Instituição é obrigatória") UUID instituicaoId) {
}
