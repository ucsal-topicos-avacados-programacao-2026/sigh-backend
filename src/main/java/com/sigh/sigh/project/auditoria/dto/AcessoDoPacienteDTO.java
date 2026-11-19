package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.time.LocalDateTime;

public record AcessoDoPacienteDTO(
		LocalDateTime ocorridoEm,
		String profissional,
		Perfil perfil,
		String instituicao,
		String finalidade,
		boolean emergencia) {
}
