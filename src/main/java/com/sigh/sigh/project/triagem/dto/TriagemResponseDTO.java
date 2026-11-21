package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SituacaoSugestao;
import com.sigh.sigh.project.triagem.entity.Triagem;

import java.time.LocalDateTime;
import java.util.UUID;

public record TriagemResponseDTO(
		UUID id,
		UUID atendimentoId,
		String sintomas,
		SinaisVitaisDTO sinaisVitais,
		CorRisco corSugerida,
		CorRisco corConfirmada,
		LocalDateTime confirmadaEm,
		boolean sugestaoPreliminar,
		SituacaoSugestao situacaoSugestao) {

	public static TriagemResponseDTO de(Triagem triagem) {
		return new TriagemResponseDTO(
				triagem.getId(),
				triagem.getAtendimento().getId(),
				triagem.getSintomas(),
				SinaisVitaisDTO.de(triagem.getSinaisVitais()),
				triagem.getCorSugerida(),
				triagem.getCorConfirmada(),
				triagem.getConfirmadaEm(),
				triagem.isSugestaoPreliminar(),
				triagem.getSituacaoSugestao());
	}
}
