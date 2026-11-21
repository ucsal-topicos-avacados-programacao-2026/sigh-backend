package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;

import java.time.LocalDateTime;
import java.util.UUID;

/** Atendimento em aberto cujo paciente não está internado: candidato a internação ou na fila de espera (FE-04). */
public record AtendimentoSemLeitoDTO(UUID atendimentoId, String paciente, LocalDateTime chegadaEm, StatusAtendimento status,
		String motivo) {
}
