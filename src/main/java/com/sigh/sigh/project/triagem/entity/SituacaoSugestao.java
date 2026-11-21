package com.sigh.sigh.project.triagem.entity;

/** Andamento da sugestão automática da IA numa triagem. Nulo quando ninguém pediu a sugestão. */
public enum SituacaoSugestao {
	EM_ANDAMENTO,
	PRONTA,
	/** A IA falhou ou demorou demais: a classificação fica com o enfermeiro, sem a sugestão. */
	INDISPONIVEL
}
