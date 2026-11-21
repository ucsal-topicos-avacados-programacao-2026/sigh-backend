package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.UUID;

public record AtendimentoEmAndamentoDTO(UUID atendimentoId, String paciente, String motivo, LocalDateTime chegadaEm,
		LocalDateTime inicioClinicoEm, CorRisco corConfirmada, String medicoResponsavel, boolean doUsuarioLogado) {
}
