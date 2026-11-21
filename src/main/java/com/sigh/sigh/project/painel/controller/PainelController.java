package com.sigh.sigh.project.painel.controller;

import com.sigh.sigh.project.painel.dto.PainelDaInstituicaoDTO;
import com.sigh.sigh.project.painel.service.PainelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/painel")
@RequiredArgsConstructor
public class PainelController {

	private static final String GESTOR_DA_REDE = "ROLE_AUDITOR_GESTOR";

	private final PainelService painelService;

	@GetMapping
	@PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO', 'ADMINISTRATIVO', 'AUDITOR_GESTOR')")
	public PainelDaInstituicaoDTO daInstituicao(@RequestParam UUID instituicaoId, Authentication usuario) {
		boolean gestorDaRede = usuario.getAuthorities().stream()
				.anyMatch(autoridade -> GESTOR_DA_REDE.equals(autoridade.getAuthority()));
		return painelService.daInstituicao(instituicaoId, usuario.getName(), gestorDaRede);
	}
}
