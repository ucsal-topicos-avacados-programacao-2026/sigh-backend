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

	@GetMapping
	@PreAuthorize("hasAnyRole('PACIENTE', 'ADMINISTRATIVO', 'AUDITOR_GESTOR')")
	public List<InstituicaoDaRedeDTO> credenciadas() {
		return instituicaoService.credenciadas();
	}
}
