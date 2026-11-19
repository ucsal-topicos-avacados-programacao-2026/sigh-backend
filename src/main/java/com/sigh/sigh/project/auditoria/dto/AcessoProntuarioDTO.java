package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.util.Set;
import java.util.UUID;

public record AcessoProntuarioDTO(
		UUID prontuarioId,
		String loginAtor,
		Perfil perfil,
		UUID instituicaoId,
		String finalidade,
		boolean emergencia,
		String justificativa,
		Set<UUID> registrosAcessadosIds) {
}
