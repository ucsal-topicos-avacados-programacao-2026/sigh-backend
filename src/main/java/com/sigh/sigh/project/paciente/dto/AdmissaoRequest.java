package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.cpf.CpfValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record AdmissaoRequest(
		@NotBlank(message = "CPF é obrigatório") @CpfValido String cpf,
		@Size(max = 150, message = "Nome deve ter no máximo 150 caracteres") String nome,
		@PastOrPresent(message = "Data de nascimento não pode estar no futuro") LocalDate dataNascimento,
		ManifestacaoInicial consentimentoInicial,
		@NotNull(message = "Instituição é obrigatória") UUID instituicaoId,
		@NotBlank(message = "Motivo é obrigatório")
		@Size(max = 2000, message = "Motivo deve ter no máximo 2000 caracteres") String motivo) {
}
