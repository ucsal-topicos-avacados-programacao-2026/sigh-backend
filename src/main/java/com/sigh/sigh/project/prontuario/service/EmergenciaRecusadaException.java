package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;

import java.io.Serial;

class EmergenciaRecusadaException extends RegraNegocioException {

	@Serial
	private static final long serialVersionUID = 1L;

	enum Motivo {
		SEM_RISCO_IMEDIATO("sem classificação vermelha ou laranja confirmada"),
		FORA_DA_JANELA("fora da janela depois da classificação"),
		QUEM_CLASSIFICOU("quem confirmou a classificação tentou usá-la");

		private final String descricao;

		Motivo(String descricao) {
			this.descricao = descricao;
		}

		String descricao() {
			return descricao;
		}
	}

	private final Motivo motivo;

	EmergenciaRecusadaException(Motivo motivo, String mensagem) {
		super(mensagem);
		this.motivo = motivo;
	}

	Motivo motivo() {
		return motivo;
	}
}
