package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;

import java.time.LocalDateTime;
import java.util.UUID;

public record AutorizacaoAdicionalResponse(UUID id, int versao, UUID instituicaoId, String instituicao,
		String categoria, UUID registroId, DecisaoConsentimento decisao, LocalDateTime vigenteDesde) {

	public static AutorizacaoAdicionalResponse de(Consentimento c) {
		return new AutorizacaoAdicionalResponse(c.getId(), c.getVersao(), c.getDestinataria().getId(),
				c.getDestinataria().getNome(), c.getCategoriaAlvo(),
				c.getRegistroAlvo() == null ? null : c.getRegistroAlvo().getId(), c.getDecisao(), c.getVigenteDesde());
	}
}
