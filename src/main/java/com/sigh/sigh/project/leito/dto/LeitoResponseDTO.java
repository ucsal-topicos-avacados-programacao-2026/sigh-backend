package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.StatusLeito;

import java.util.UUID;

public record LeitoResponseDTO(
		UUID id,
		UUID instituicaoId,
		String codigo,
		String setor,
		CategoriaLeito categoria,
		StatusLeito status) {

	public static LeitoResponseDTO de(Leito leito) {
		return new LeitoResponseDTO(
				leito.getId(),
				leito.getInstituicao().getId(),
				leito.getCodigo(),
				leito.getSetor(),
				leito.getCategoria(),
				leito.getStatus());
	}
}
