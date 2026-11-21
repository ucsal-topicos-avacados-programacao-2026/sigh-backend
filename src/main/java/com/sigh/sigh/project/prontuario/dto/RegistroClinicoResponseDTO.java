package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegistroClinicoResponseDTO(
		UUID id,
		UUID atendimentoId,
		TipoRegistro tipo,
		LocalDateTime criadoEm,
		String conteudo,
		String categoriaInformacao,
		String codigoCID10) {

	public static RegistroClinicoResponseDTO de(RegistroClinico registro) {
		return new RegistroClinicoResponseDTO(
				registro.getId(),
				registro.getAtendimento().getId(),
				registro.getTipo(),
				registro.getCriadoEm(),
				registro.getConteudo(),
				registro.getCategoriaInformacao(),
				registro.getCodigoCID10());
	}
}
