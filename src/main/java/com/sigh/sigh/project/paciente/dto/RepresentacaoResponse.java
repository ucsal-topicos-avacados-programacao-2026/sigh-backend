package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;

import java.time.LocalDateTime;
import java.util.UUID;

public record RepresentacaoResponse(UUID id, String representante, String documento, LocalDateTime inicioEm,
		LocalDateTime fimEm, boolean vigente) {

	public static RepresentacaoResponse de(RepresentacaoLegal r, LocalDateTime agora) {
		return new RepresentacaoResponse(r.getId(), r.getRepresentante().getNome(), r.getDocumento(), r.getInicioEm(),
				r.getFimEm(), r.validarVigencia(agora));
	}
}
