package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConsentimentoResponse(UUID id, int versao, TipoConsentimento tipo, DecisaoConsentimento decisao,
		LocalDateTime vigenteDesde) {

	public static ConsentimentoResponse de(Consentimento c) {
		return new ConsentimentoResponse(c.getId(), c.getVersao(), c.getTipo(), c.getDecisao(), c.getVigenteDesde());
	}
}
