package com.sigh.sigh.project.prontuario.controller;

import com.sigh.sigh.project.prontuario.dto.AcessoEmergenciaRequestDTO;
import com.sigh.sigh.project.prontuario.dto.HistoricoDoAtendimentoDTO;
import com.sigh.sigh.project.prontuario.service.HistoricoAtendimentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/atendimentos/{atendimentoId}/historico")
@PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
@RequiredArgsConstructor
public class HistoricoAtendimentoController {

	private final HistoricoAtendimentoService historicoAtendimentoService;

	@GetMapping
	public HistoricoDoAtendimentoDTO consultar(@PathVariable UUID atendimentoId, Principal profissional) {
		return historicoAtendimentoService.consultar(atendimentoId, profissional.getName());
	}

	@PostMapping("/emergencia")
	public HistoricoDoAtendimentoDTO acessarEmEmergencia(@PathVariable UUID atendimentoId,
			@RequestBody @Valid AcessoEmergenciaRequestDTO dados, Principal profissional) {
		return historicoAtendimentoService.acessarEmEmergencia(atendimentoId, dados.justificativa(),
				profissional.getName());
	}
}
