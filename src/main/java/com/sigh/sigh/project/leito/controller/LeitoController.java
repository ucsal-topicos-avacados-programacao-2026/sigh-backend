package com.sigh.sigh.project.leito.controller;

import com.sigh.sigh.project.leito.dto.AlteracaoStatusLeitoRequestDTO;
import com.sigh.sigh.project.leito.dto.FiltroLeitoDTO;
import com.sigh.sigh.project.leito.dto.LeitoRequestDTO;
import com.sigh.sigh.project.leito.dto.LeitoResponseDTO;
import com.sigh.sigh.project.leito.service.LeitoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/leitos")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
@RequiredArgsConstructor
public class LeitoController {

	private final LeitoService leitoService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LeitoResponseDTO cadastrar(@RequestBody @Valid LeitoRequestDTO dados) {
		return leitoService.cadastrar(dados);
	}

	@GetMapping
	public PagedModel<LeitoResponseDTO> buscar(FiltroLeitoDTO filtro, Pageable paginacao) {
		return new PagedModel<>(leitoService.buscar(filtro, paginacao));
	}

	@PatchMapping("/{leitoId}/status")
	public LeitoResponseDTO alterarStatus(@PathVariable UUID leitoId,
			@RequestBody @Valid AlteracaoStatusLeitoRequestDTO dados) {
		return leitoService.alterarStatus(leitoId, dados.status());
	}
}
