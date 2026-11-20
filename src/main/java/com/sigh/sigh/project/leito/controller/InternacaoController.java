package com.sigh.sigh.project.leito.controller;

import com.sigh.sigh.project.leito.dto.AltaRequestDTO;
import com.sigh.sigh.project.leito.dto.InternacaoRequestDTO;
import com.sigh.sigh.project.leito.dto.InternacaoResponseDTO;
import com.sigh.sigh.project.leito.dto.TransferenciaRequestDTO;
import com.sigh.sigh.project.leito.service.InternacaoService;
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

import java.util.UUID;

@RestController
@RequestMapping("/internacoes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
@RequiredArgsConstructor
public class InternacaoController {

	private final InternacaoService internacaoService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InternacaoResponseDTO internar(@RequestBody @Valid InternacaoRequestDTO dados) {
		return internacaoService.internar(dados);
	}

	@PostMapping("/{internacaoId}/transferencia")
	public InternacaoResponseDTO transferir(@PathVariable UUID internacaoId,
			@RequestBody @Valid TransferenciaRequestDTO dados) {
		return internacaoService.transferir(internacaoId, dados.leitoDestinoId());
	}

	@PostMapping("/{internacaoId}/alta")
	public InternacaoResponseDTO darAlta(@PathVariable UUID internacaoId,
			@RequestBody @Valid AltaRequestDTO dados) {
		return internacaoService.darAlta(internacaoId, dados.resumoAltaId());
	}
}
