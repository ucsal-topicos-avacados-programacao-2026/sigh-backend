package com.sigh.sigh.project.prontuario.dto;

import java.util.List;

public record HistoricoDoAtendimentoDTO(
		List<RegistroDoHistoricoDTO> registros,
		boolean possuiInformacoesRestritas,
		boolean acessoDeEmergencia) {
}
