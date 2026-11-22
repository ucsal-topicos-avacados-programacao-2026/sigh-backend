package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record VinculoRequest(
		@NotNull(message = "Instituição é obrigatória") UUID instituicaoId,
		@NotEmpty(message = "Informe ao menos um perfil no vínculo") Set<Perfil> perfis,
		@Size(max = 30, message = "Registro profissional deve ter no máximo 30 caracteres") String registroProfissional) {
}
