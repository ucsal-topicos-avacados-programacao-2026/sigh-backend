package com.sigh.sigh.project.leito.controller;

import com.sigh.sigh.project.leito.dto.AceiteTransferenciaRequestDTO;
import com.sigh.sigh.project.leito.dto.RecusaTransferenciaRequestDTO;
import com.sigh.sigh.project.leito.dto.SolicitacaoTransferenciaResponseDTO;
import com.sigh.sigh.project.leito.service.InternacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * Transferência entre instituições da rede (RF-006, BE-21): o destino vê os pedidos recebidos e aceita
 * (escolhendo um leito livre dele) ou recusa; a origem pode cancelar enquanto estiver pendente.
 */
@RestController
@RequestMapping("/solicitacoes-transferencia")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
@RequiredArgsConstructor
public class SolicitacaoTransferenciaController {

	private final InternacaoService internacaoService;

	@GetMapping("/recebidas")
	public PagedModel<SolicitacaoTransferenciaResponseDTO> recebidas(@RequestParam UUID instituicaoId,
			Pageable paginacao, Principal usuario) {
		return new PagedModel<>(internacaoService.solicitacoesRecebidas(instituicaoId, usuario.getName(), paginacao));
	}

	/** A origem acompanha os pedidos que fez: pendentes, aceitos, recusados (com o motivo) e cancelados. */
	@GetMapping("/enviadas")
	public PagedModel<SolicitacaoTransferenciaResponseDTO> enviadas(@RequestParam UUID instituicaoId,
			Pageable paginacao, Principal usuario) {
		return new PagedModel<>(internacaoService.solicitacoesEnviadas(instituicaoId, usuario.getName(), paginacao));
	}

	@PostMapping("/{solicitacaoId}/aceite")
	public SolicitacaoTransferenciaResponseDTO aceitar(@PathVariable UUID solicitacaoId,
			@RequestBody @Valid AceiteTransferenciaRequestDTO dados, Principal usuario) {
		return internacaoService.aceitarTransferencia(solicitacaoId, dados.leitoId(), usuario.getName());
	}

	@PostMapping("/{solicitacaoId}/recusa")
	public SolicitacaoTransferenciaResponseDTO recusar(@PathVariable UUID solicitacaoId,
			@RequestBody @Valid RecusaTransferenciaRequestDTO dados, Principal usuario) {
		return internacaoService.recusarTransferencia(solicitacaoId, dados.motivo(), usuario.getName());
	}

	@PostMapping("/{solicitacaoId}/cancelamento")
	public SolicitacaoTransferenciaResponseDTO cancelar(@PathVariable UUID solicitacaoId, Principal usuario) {
		return internacaoService.cancelarTransferencia(solicitacaoId, usuario.getName());
	}
}
