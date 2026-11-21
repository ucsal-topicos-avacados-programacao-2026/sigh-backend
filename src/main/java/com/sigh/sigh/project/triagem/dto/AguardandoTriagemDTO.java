package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SituacaoSugestao;

import java.time.LocalDateTime;
import java.util.UUID;

public record AguardandoTriagemDTO(UUID atendimentoId, String paciente, LocalDateTime chegadaEm, String motivo,
		TriagemPendente triagemPendente) {

	public record TriagemPendente(UUID id, String sintomas, SinaisVitaisDTO sinaisVitais, CorRisco corSugerida,
			boolean sugestaoPreliminar, SituacaoSugestao situacaoSugestao) {
	}
}
