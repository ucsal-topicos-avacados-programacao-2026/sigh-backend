package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.DadosInvalidosException;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Alvo de uma restrição ou autorização adicional (RF-009): exatamente uma categoria de informação ou
 * um registro do próprio prontuário. Registro de outro prontuário responde como inexistente.
 */
record AlvoDeRestricao(String categoria, RegistroClinico registro) {

	static AlvoDeRestricao de(String categoria, UUID registroId, Prontuario prontuario,
			RegistroClinicoRepository registroClinicoRepository) {
		String categoriaInformada = categoria == null || categoria.isBlank() ? null : categoria.strip();
		if ((categoriaInformada == null) == (registroId == null)) {
			throw new DadosInvalidosException(Map.of("alvo", "informe uma categoria ou um registro, não os dois"));
		}
		if (categoriaInformada != null) {
			return new AlvoDeRestricao(categoriaInformada, null);
		}
		RegistroClinico registro = registroClinicoRepository.findById(registroId)
				.filter(encontrado -> encontrado.getProntuario().getId().equals(prontuario.getId()))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Registro não encontrado."));
		return new AlvoDeRestricao(null, registro);
	}
}
