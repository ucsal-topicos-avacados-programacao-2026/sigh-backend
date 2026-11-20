package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** Registro clínico como aparece num histórico, com a instituição de origem e se está restrito pelo paciente. */
public record RegistroDoHistoricoDTO(
		UUID id,
		TipoRegistro tipo,
		LocalDateTime criadoEm,
		String conteudo,
		String categoriaInformacao,
		String codigoCID10,
		UUID instituicaoOrigemId,
		String instituicaoOrigem,
		boolean restrito) {

	public static RegistroDoHistoricoDTO de(RegistroClinico registro, Set<UUID> restritos) {
		return new RegistroDoHistoricoDTO(
				registro.getId(),
				registro.getTipo(),
				registro.getCriadoEm(),
				registro.getConteudo(),
				registro.getCategoriaInformacao(),
				registro.getCodigoCID10(),
				registro.getOrigem().getId(),
				registro.getOrigem().getNome(),
				restritos.contains(registro.getId()));
	}
}
