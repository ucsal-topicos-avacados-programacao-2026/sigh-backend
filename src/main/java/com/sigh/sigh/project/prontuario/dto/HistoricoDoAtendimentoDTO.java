package com.sigh.sigh.project.prontuario.dto;

import java.util.List;

/**
 * Histórico visto pelo profissional no atendimento. {@code possuiInformacoesRestritas} avisa que há
 * registros escondidos pela restrição do paciente, que só um acesso de emergência mostraria.
 */
public record HistoricoDoAtendimentoDTO(
		List<RegistroDoHistoricoDTO> registros,
		boolean possuiInformacoesRestritas,
		boolean acessoDeEmergencia) {
}
