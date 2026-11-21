package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.SolicitacaoTransferencia;
import com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia;

import java.time.LocalDateTime;
import java.util.UUID;

public record SolicitacaoTransferenciaResponseDTO(
		UUID id,
		UUID internacaoId,
		String paciente,
		UUID instituicaoOrigemId,
		String instituicaoOrigem,
		UUID instituicaoDestinoId,
		CategoriaLeito categoria,
		StatusSolicitacaoTransferencia status,
		LocalDateTime solicitadaEm,
		LocalDateTime decididaEm,
		UUID leitoDestinoId,
		String motivoRecusa) {

	public static SolicitacaoTransferenciaResponseDTO de(SolicitacaoTransferencia solicitacao) {
		return new SolicitacaoTransferenciaResponseDTO(solicitacao.getId(), solicitacao.getInternacao().getId(),
				solicitacao.getInternacao().getProntuario().getPaciente().getNome(),
				solicitacao.getInstituicaoOrigem().getId(), solicitacao.getInstituicaoOrigem().getNome(),
				solicitacao.getInstituicaoDestino().getId(), solicitacao.getCategoria(), solicitacao.getStatus(),
				solicitacao.getSolicitadaEm(), solicitacao.getDecididaEm(),
				solicitacao.getLeitoDestino() == null ? null : solicitacao.getLeitoDestino().getId(),
				solicitacao.getMotivoRecusa());
	}
}
