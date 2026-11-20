package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;

/** Manifestação do paciente sobre o compartilhamento amplo no primeiro cadastro (RF-002). */
public enum ManifestacaoInicial {
	AUTORIZADO(DecisaoConsentimento.AUTORIZADO),
	NEGADO(DecisaoConsentimento.NEGADO);

	private final DecisaoConsentimento decisao;

	ManifestacaoInicial(DecisaoConsentimento decisao) {
		this.decisao = decisao;
	}

	public DecisaoConsentimento decisao() {
		return decisao;
	}
}
