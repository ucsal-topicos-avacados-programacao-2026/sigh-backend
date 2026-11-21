package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Atendimento da instituição que ainda não tem classificação de risco confirmada (FE-06): o enfermeiro faz a
 * triagem dele, ou retoma a que começou e não confirmou.
 *
 * @param triagemPendente triagem registrada e ainda sem confirmação; null se a triagem não começou
 */
public record AguardandoTriagemDTO(UUID atendimentoId, String paciente, LocalDateTime chegadaEm, String motivo,
		TriagemPendente triagemPendente) {

	/** Triagem começada: os sintomas registrados e a sugestão da IA, se já houver. */
	public record TriagemPendente(UUID id, String sintomas, CorRisco corSugerida) {
	}
}
