package com.sigh.sigh.project.leito.controller;

import com.sigh.sigh.project.leito.dto.AltaRequestDTO;
import com.sigh.sigh.project.leito.dto.InternacaoRequestDTO;
import com.sigh.sigh.project.leito.dto.InternacaoResponseDTO;
import com.sigh.sigh.project.leito.dto.SolicitacaoTransferenciaRequestDTO;
import com.sigh.sigh.project.leito.dto.SolicitacaoTransferenciaResponseDTO;
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

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/internacoes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
@RequiredArgsConstructor
public class InternacaoController {

	private final InternacaoService internacaoService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InternacaoResponseDTO internar(@RequestBody @Valid InternacaoRequestDTO dados, Principal usuario) {
		return internacaoService.internar(dados, usuario.getName());
	}

	@PostMapping("/{internacaoId}/transferencia")
	public InternacaoResponseDTO transferir(@PathVariable UUID internacaoId,
			@RequestBody @Valid TransferenciaRequestDTO dados, Principal usuario) {
		return internacaoService.transferir(internacaoId, dados.leitoDestinoId(), usuario.getName());
	}

	/** Transferência para outra instituição da rede: pedido que o destino aceita ou recusa (BE-21). */
	@PostMapping("/{internacaoId}/solicitacoes-transferencia")
	@ResponseStatus(HttpStatus.CREATED)
	public SolicitacaoTransferenciaResponseDTO solicitarTransferencia(@PathVariable UUID internacaoId,
			@RequestBody @Valid SolicitacaoTransferenciaRequestDTO dados, Principal usuario) {
		return internacaoService.solicitarTransferencia(internacaoId, dados, usuario.getName());
	}

	@PostMapping("/{internacaoId}/alta")
	public InternacaoResponseDTO darAlta(@PathVariable UUID internacaoId,
			@RequestBody @Valid AltaRequestDTO dados, Principal usuario) {
		return internacaoService.darAlta(internacaoId, dados.resumoAltaId(), usuario.getName());
	}
}
