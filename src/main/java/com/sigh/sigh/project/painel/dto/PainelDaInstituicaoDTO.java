package com.sigh.sigh.project.painel.dto;

import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Números da instituição agora, para o painel (FE-09). Só contagens e tempos: nenhum nome, CPF, motivo ou dado
 * clínico de paciente.
 */
public record PainelDaInstituicaoDTO(
		UUID instituicaoId,
		String instituicaoNome,
		TipoInstituicao tipo,
		LocalDateTime geradoEm,
		/** Leitos por categoria e situação; só as combinações que existem. */
		List<LeitosDoPainelDTO> leitos,
		EsperaDTO aguardandoTriagem,
		/** Fila do médico por cor de risco, nas cinco cores, da mais grave para a menos grave. */
		List<RiscoNaFilaDTO> fila,
		long emAtendimento,
		long internados,
		long chegadasHoje,
		TransferenciasPendentesDTO transferencias) {

	/** Quantos esperam e há quantos minutos espera o que chegou primeiro (nulo sem ninguém). */
	public record EsperaDTO(long quantidade, Long maiorEsperaMinutos) {
	}

	/**
	 * Quantos esperam o médico com a cor, a maior espera desde a chegada, o tempo máximo da cor e quantos já
	 * passaram dele (o mesmo critério da fila do enfermeiro).
	 */
	public record RiscoNaFilaDTO(CorRisco cor, long quantidade,
			Long maiorEsperaMinutos, long tempoMaximoMinutos, long acimaDoTempoMaximo) {
	}

	/** Pedidos de transferência entre instituições ainda sem resposta. */
	public record TransferenciasPendentesDTO(long recebidas, long enviadas) {
	}
}
