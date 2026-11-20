package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.Internacao;
import com.sigh.sigh.project.leito.entity.Leito;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternacaoResponseDTO(
		UUID id,
		UUID prontuarioId,
		UUID leitoId,
		LocalDateTime admitidaEm,
		LocalDateTime altaEm) {

	public static InternacaoResponseDTO de(Internacao internacao, Leito leito) {
		return new InternacaoResponseDTO(
				internacao.getId(),
				internacao.getProntuario().getId(),
				leito.getId(),
				internacao.getAdmitidaEm(),
				internacao.getAltaEm());
	}
}
