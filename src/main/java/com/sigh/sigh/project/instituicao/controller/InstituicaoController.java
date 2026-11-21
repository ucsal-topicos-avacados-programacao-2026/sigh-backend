package com.sigh.sigh.project.instituicao.controller;

import com.sigh.sigh.project.instituicao.dto.InstituicaoDaRedeDTO;
import com.sigh.sigh.project.instituicao.service.InstituicaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instituicoes")
@RequiredArgsConstructor
public class InstituicaoController {

	private final InstituicaoService instituicaoService;

	/**
	 * Instituições credenciadas da rede: o paciente escolhe a quem liberar uma informação restrita (autorização
	 * adicional, RF-009; FE-05), e o administrativo escolhe o destino de uma transferência (RF-006; FE-04).
	 * Só nome e id: não traz dado de paciente nem de profissional.
	 */
	@GetMapping
	@PreAuthorize("hasAnyRole('PACIENTE', 'ADMINISTRATIVO')")
	public List<InstituicaoDaRedeDTO> credenciadas() {
		return instituicaoService.credenciadas();
	}
}
