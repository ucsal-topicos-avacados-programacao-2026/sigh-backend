package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;

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
