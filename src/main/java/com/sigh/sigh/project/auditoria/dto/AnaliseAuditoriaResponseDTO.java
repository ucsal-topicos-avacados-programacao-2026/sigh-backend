package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.auditoria.entity.AnaliseAuditoria;
import com.sigh.sigh.project.auditoria.entity.ConclusaoAnalise;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnaliseAuditoriaResponseDTO(
		UUID id,
		UUID registroAuditoriaId,
		TipoAnalise tipo,
		ConclusaoAnalise conclusao,
		String descricao,
		LocalDateTime registradaEm) {

	public static AnaliseAuditoriaResponseDTO de(AnaliseAuditoria analise) {
		return new AnaliseAuditoriaResponseDTO(
				analise.getId(),
				analise.getRegistroAuditoria().getId(),
				analise.getTipo(),
				analise.getConclusao(),
				analise.getDescricao(),
				analise.getRegistradaEm());
	}
}
