package com.sigh.sigh.project.prontuario.controller;

import com.sigh.sigh.project.prontuario.dto.AtendimentoEmAndamentoDTO;
import com.sigh.sigh.project.prontuario.service.AtendimentosEmAndamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/atendimentos")
@RequiredArgsConstructor
public class AtendimentosEmAndamentoController {

	private final AtendimentosEmAndamentoService atendimentosEmAndamentoService;

	@GetMapping("/em-andamento")
	@PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
	public List<AtendimentoEmAndamentoDTO> emAndamento(@RequestParam UUID instituicaoId, Principal usuario) {
		return atendimentosEmAndamentoService.emAndamento(instituicaoId, usuario.getName());
	}
}
