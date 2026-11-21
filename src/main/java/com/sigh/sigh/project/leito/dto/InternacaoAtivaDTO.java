package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InternacaoAtivaDTO(
		UUID id,
		UUID atendimentoId,
		String paciente,
		UUID leitoId,
		String leitoCodigo,
		String setor,
		CategoriaLeito categoria,
		LocalDateTime admitidaEm,
		List<ResumoDeAltaDTO> resumosDeAlta,
		UUID solicitacaoPendenteId) {

	public record ResumoDeAltaDTO(UUID id, LocalDateTime criadoEm, String autor) {
	}
}
