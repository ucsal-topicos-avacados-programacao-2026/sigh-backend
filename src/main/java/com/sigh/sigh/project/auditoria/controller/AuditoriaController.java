package com.sigh.sigh.project.auditoria.controller;

import com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO;
import com.sigh.sigh.project.auditoria.dto.FiltroAuditoriaDTO;
import com.sigh.sigh.project.auditoria.dto.IndicadorInstituicaoDTO;
import com.sigh.sigh.project.auditoria.dto.PeriodoDTO;
import com.sigh.sigh.project.auditoria.dto.RegistroAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.service.ConsultaAuditoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

	private final ConsultaAuditoriaService consultaAuditoriaService;

	@GetMapping("/meus-acessos")
	@PreAuthorize("hasRole('PACIENTE')")
	public PagedModel<AcessoDoPacienteDTO> consultarMeusAcessos(Principal paciente, Pageable paginacao) {
		return new PagedModel<>(consultaAuditoriaService.consultarAcessosDoPaciente(paciente.getName(), paginacao));
	}

	@GetMapping("/registros")
	@PreAuthorize("hasRole('AUDITOR_GESTOR')")
	public PagedModel<RegistroAuditoriaResponseDTO> consultarRegistros(@Valid FiltroAuditoriaDTO filtro,
			Pageable paginacao) {
		return new PagedModel<>(consultaAuditoriaService.consultarRegistros(filtro, paginacao));
	}

	@GetMapping("/indicadores")
	@PreAuthorize("hasRole('AUDITOR_GESTOR')")
	public List<IndicadorInstituicaoDTO> consultarIndicadores(@Valid PeriodoDTO periodo) {
		return consultaAuditoriaService.consultarIndicadores(periodo);
	}
}
