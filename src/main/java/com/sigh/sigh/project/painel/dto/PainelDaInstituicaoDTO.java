package com.sigh.sigh.project.painel.dto;

import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PainelDaInstituicaoDTO(
		UUID instituicaoId,
		String instituicaoNome,
		TipoInstituicao tipo,
		LocalDateTime geradoEm,
		List<LeitosDoPainelDTO> leitos,
		EsperaDTO aguardandoTriagem,
		List<RiscoNaFilaDTO> fila,
		long emAtendimento,
		long internados,
		long chegadasHoje,
		TransferenciasPendentesDTO transferencias) {

	public record EsperaDTO(long quantidade, Long maiorEsperaMinutos) {
	}

	public record RiscoNaFilaDTO(CorRisco cor, long quantidade,
			Long maiorEsperaMinutos, long tempoMaximoMinutos, long acimaDoTempoMaximo) {
	}

	public record TransferenciasPendentesDTO(long recebidas, long enviadas) {
	}
}
