package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.UUID;

public record PacienteNaFilaDTO(
		UUID triagemId,
		UUID atendimentoId,
		String pacienteNome,
		CorRisco corConfirmada,
		LocalDateTime chegadaEm,
		long minutosDeEspera,
		boolean tempoMaximoExcedido) {
}
