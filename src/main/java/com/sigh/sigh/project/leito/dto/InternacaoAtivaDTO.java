package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internação em andamento num leito da instituição, como o administrativo a gerencia (FE-04): o leito, o paciente,
 * os resumos de alta já feitos desde a admissão (sem o conteúdo clínico) e o pedido de transferência pendente.
 *
 * @param solicitacaoPendenteId pedido de transferência para outra instituição ainda sem decisão; null se não há
 */
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

	/** Resumo de alta disponível para anexar: só quem fez e quando, sem o texto. */
	public record ResumoDeAltaDTO(UUID id, LocalDateTime criadoEm, String autor) {
	}
}
