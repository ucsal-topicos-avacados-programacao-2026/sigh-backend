package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.AcaoAdministrativa;
import com.sigh.sigh.project.usuario.entity.TipoDeAcaoAdministrativa;

import java.time.LocalDateTime;
import java.util.UUID;

public record AcaoAdministrativaDTO(UUID id, String ator, String alvo, TipoDeAcaoAdministrativa acao, String detalhe,
		LocalDateTime ocorridoEm) {

	public static AcaoAdministrativaDTO de(AcaoAdministrativa acao) {
		return new AcaoAdministrativaDTO(acao.getId(), acao.getAtor().getLogin(), acao.getAlvo().getLogin(),
				acao.getAcao(), acao.getDetalhe(), acao.getOcorridoEm());
	}
}
