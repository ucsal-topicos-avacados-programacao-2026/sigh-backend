package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
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

	/** A partir do registro já carregado (com a justificativa decifrada) e com ator e instituição buscados. */
	public static RegistroAuditoriaResponseDTO de(RegistroAuditoria registro) {
		return new RegistroAuditoriaResponseDTO(registro.getId(),
				registro.getProntuario() == null ? null : registro.getProntuario().getId(), registro.getAtor().getNome(),
				registro.getPerfil(), registro.getInstituicao() == null ? null : registro.getInstituicao().getNome(),
				registro.getOcorridoEm(), registro.getFinalidadeDeclarada(), registro.isEmergencia(),
				registro.getJustificativa());
	}
}
