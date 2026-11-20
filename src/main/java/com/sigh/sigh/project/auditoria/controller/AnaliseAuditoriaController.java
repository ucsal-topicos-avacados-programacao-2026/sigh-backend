package com.sigh.sigh.project.auditoria.controller;

import com.sigh.sigh.project.auditoria.dto.AnaliseAuditoriaRequestDTO;
import com.sigh.sigh.project.auditoria.dto.AnaliseAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;
import com.sigh.sigh.project.auditoria.service.AnaliseAuditoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/auditoria")
@PreAuthorize("hasRole('AUDITOR_GESTOR')")
@RequiredArgsConstructor
public class AnaliseAuditoriaController {

	private final AnaliseAuditoriaService analiseAuditoriaService;

	@PostMapping("/registros/{registroAuditoriaId}/analises")
	@ResponseStatus(HttpStatus.CREATED)
	public AnaliseAuditoriaResponseDTO registrar(@PathVariable UUID registroAuditoriaId,
			@RequestBody @Valid AnaliseAuditoriaRequestDTO dados, Principal auditor) {
		return analiseAuditoriaService.registrar(registroAuditoriaId, dados, auditor.getName());
	}

	@GetMapping("/analises")
	public PagedModel<AnaliseAuditoriaResponseDTO> listar(@RequestParam TipoAnalise tipo, Pageable paginacao) {
		return new PagedModel<>(analiseAuditoriaService.listar(tipo, paginacao));
	}
}
