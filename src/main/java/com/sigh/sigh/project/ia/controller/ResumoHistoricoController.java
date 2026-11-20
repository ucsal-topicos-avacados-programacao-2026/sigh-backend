package com.sigh.sigh.project.ia.controller;

import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import com.sigh.sigh.project.ia.service.GeracaoResumoService;
import com.sigh.sigh.project.ia.service.ResumoHistoricoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('MEDICO')")
@RequiredArgsConstructor
public class ResumoHistoricoController {

	private final GeracaoResumoService geracaoResumoService;
	private final ResumoHistoricoService resumoHistoricoService;

	@PostMapping("/atendimentos/{atendimentoId}/resumos")
	@ResponseStatus(HttpStatus.CREATED)
	public ResumoHistoricoResponseDTO gerar(@PathVariable UUID atendimentoId, Principal medico) {
		return geracaoResumoService.gerar(atendimentoId, medico.getName());
	}

	@GetMapping("/resumos/{resumoId}")
	public ResumoHistoricoResponseDTO consultar(@PathVariable UUID resumoId, Principal medico) {
		return resumoHistoricoService.consultar(resumoId, medico.getName());
	}

	@PostMapping("/resumos/{resumoId}/validacao")
	public ResumoHistoricoResponseDTO validar(@PathVariable UUID resumoId, Principal medico) {
		return resumoHistoricoService.validar(resumoId, medico.getName());
	}
}
