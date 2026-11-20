package com.sigh.sigh.project.auditoria.dto;

import java.util.UUID;

public record IndicadorInstituicaoDTO(
		UUID instituicaoId,
		String instituicao,
		long totalAcessos,
		long acessosEmergencia) {
}
