package com.sigh.sigh.project.usuario.controller;

import com.sigh.sigh.project.usuario.dto.AcaoAdministrativaDTO;
import com.sigh.sigh.project.usuario.dto.AtivoRequest;
import com.sigh.sigh.project.usuario.dto.CriarUsuarioRequest;
import com.sigh.sigh.project.usuario.dto.CriarUsuarioResponse;
import com.sigh.sigh.project.usuario.dto.SenhaTemporariaResponse;
import com.sigh.sigh.project.usuario.dto.UsuarioDetalheDTO;
import com.sigh.sigh.project.usuario.dto.VinculoRequest;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.GestaoDeUsuariosService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AUDITOR_GESTOR')")
public class GestaoDeUsuariosController {

	private final GestaoDeUsuariosService gestaoDeUsuariosService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CriarUsuarioResponse criar(@RequestBody @Valid CriarUsuarioRequest dados, Principal auditor) {
		return gestaoDeUsuariosService.criar(dados, auditor.getName());
	}

	@GetMapping
	public PagedModel<UsuarioDetalheDTO> listar(@RequestParam(defaultValue = "") String busca,
			@RequestParam(required = false) Perfil perfil, @RequestParam(required = false) Boolean ativo,
			Pageable paginacao) {
		return new PagedModel<>(gestaoDeUsuariosService.listar(busca, perfil, ativo, paginacao));
	}

	@GetMapping("/acoes")
	public PagedModel<AcaoAdministrativaDTO> acoes(Pageable paginacao) {
		return new PagedModel<>(gestaoDeUsuariosService.acoes(paginacao));
	}

	@PatchMapping("/{usuarioId}/ativo")
	public UsuarioDetalheDTO alterarAtivo(@PathVariable UUID usuarioId, @RequestBody @Valid AtivoRequest dados,
			Principal auditor) {
		return gestaoDeUsuariosService.alterarAtivo(usuarioId, dados.ativo(), auditor.getName());
	}

	@PostMapping("/{usuarioId}/senha-temporaria")
	public SenhaTemporariaResponse redefinirSenha(@PathVariable UUID usuarioId, Principal auditor) {
		return gestaoDeUsuariosService.redefinirSenha(usuarioId, auditor.getName());
	}

	@PostMapping("/{usuarioId}/segundo-fator/reinicio")
	public UsuarioDetalheDTO zerarSegundoFator(@PathVariable UUID usuarioId, Principal auditor) {
		return gestaoDeUsuariosService.zerarSegundoFator(usuarioId, auditor.getName());
	}

	@PostMapping("/{usuarioId}/vinculos")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioDetalheDTO adicionarVinculo(@PathVariable UUID usuarioId, @RequestBody @Valid VinculoRequest dados,
			Principal auditor) {
		return gestaoDeUsuariosService.adicionarVinculo(usuarioId, dados, auditor.getName());
	}

	@PostMapping("/{usuarioId}/vinculos/{vinculoId}/encerramento")
	public UsuarioDetalheDTO encerrarVinculo(@PathVariable UUID usuarioId, @PathVariable UUID vinculoId,
			Principal auditor) {
		return gestaoDeUsuariosService.encerrarVinculo(usuarioId, vinculoId, auditor.getName());
	}
}
