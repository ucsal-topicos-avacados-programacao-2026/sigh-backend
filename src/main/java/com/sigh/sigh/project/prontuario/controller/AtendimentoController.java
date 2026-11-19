package com.sigh.sigh.project.prontuario.controller;

import com.sigh.sigh.project.prontuario.dto.RegistroClinicoRequestDTO;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoResponseDTO;
import com.sigh.sigh.project.prontuario.service.AtendimentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/atendimentos/{atendimentoId}")
@PreAuthorize("hasRole('MEDICO')")
@RequiredArgsConstructor
public class AtendimentoController {

	private final AtendimentoService atendimentoService;

	@PostMapping("/registros")
	@ResponseStatus(HttpStatus.CREATED)
	public RegistroClinicoResponseDTO registrar(@PathVariable UUID atendimentoId,
			@RequestBody @Valid RegistroClinicoRequestDTO dados, Principal medico) {
		return atendimentoService.registrar(atendimentoId, dados, medico.getName());
	}

	@PostMapping("/encerramento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void encerrar(@PathVariable UUID atendimentoId) {
		atendimentoService.encerrar(atendimentoId);
	}
}
