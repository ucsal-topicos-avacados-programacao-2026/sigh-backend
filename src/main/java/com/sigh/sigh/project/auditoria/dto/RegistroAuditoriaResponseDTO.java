package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegistroAuditoriaResponseDTO(
		UUID id,
		UUID prontuarioId,
		String profissional,
		Perfil perfil,
		String instituicao,
		LocalDateTime ocorridoEm,
		String finalidade,
		boolean emergencia,
		String justificativa) {
}
