package com.sigh.sigh.project.paciente.controller;

import com.sigh.sigh.project.paciente.dto.ConsentimentoResponse;
import com.sigh.sigh.project.paciente.dto.ConsentimentosDoPacienteResponse;
import com.sigh.sigh.project.paciente.dto.DecisaoPacienteRequest;
import com.sigh.sigh.project.paciente.service.ConsentimentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** O paciente consulta, revisa e revoga o próprio consentimento (RF-002, RN-007). */
@RestController
@RequestMapping("/api/pacientes/me/consentimentos")
public class ConsentimentoController {

	private final ConsentimentoService consentimentoService;

	public ConsentimentoController(ConsentimentoService consentimentoService) {
		this.consentimentoService = consentimentoService;
	}

	@GetMapping
	@PreAuthorize("hasRole('PACIENTE')")
	public ConsentimentosDoPacienteResponse consultar(Authentication autenticacao) {
		return consentimentoService.consultarDoPaciente(autenticacao.getName());
	}

	@PostMapping
	@PreAuthorize("hasRole('PACIENTE')")
	@ResponseStatus(HttpStatus.CREATED)
	public ConsentimentoResponse decidir(@Valid @RequestBody DecisaoPacienteRequest requisicao,
			Authentication autenticacao) {
		return consentimentoService.registrarDecisaoDoPaciente(autenticacao.getName(), requisicao.decisao());
	}
}
