package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;

import java.time.LocalDateTime;
import java.util.UUID;

public record AtendimentoSemLeitoDTO(UUID atendimentoId, String paciente, LocalDateTime chegadaEm, StatusAtendimento status,
		String motivo) {
}
