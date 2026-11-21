package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Atendimento em andamento na instituição (FE-07): já começou com o médico e saiu da fila de triagem.
 *
 * @param corConfirmada classificação de risco confirmada mais recente; null se não houver
 * @param doUsuarioLogado se o médico responsável é quem consulta
 */
public record AtendimentoEmAndamentoDTO(UUID atendimentoId, String paciente, String motivo, LocalDateTime chegadaEm,
		LocalDateTime inicioClinicoEm, CorRisco corConfirmada, String medicoResponsavel, boolean doUsuarioLogado) {
}
