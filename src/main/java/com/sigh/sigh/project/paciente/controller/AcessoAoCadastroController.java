package com.sigh.sigh.project.paciente.controller;

import com.sigh.sigh.project.paciente.dto.InstituicaoDoAdministrativoRequest;
import com.sigh.sigh.project.paciente.dto.RepresentacaoRequest;
import com.sigh.sigh.project.paciente.dto.RepresentacaoResponse;
import com.sigh.sigh.project.paciente.dto.VinculoContaRequest;
import com.sigh.sigh.project.paciente.service.AcessoAoCadastroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.UUID;

/** A Recepção dá acesso ao cadastro: conta do paciente e representante legal (RF-002, RF-009). */
@RestController
@RequestMapping("/api/pacientes/{pacienteId}")
@PreAuthorize("hasRole('RECEPCAO')")
@RequiredArgsConstructor
public class AcessoAoCadastroController {

	private final AcessoAoCadastroService acessoAoCadastroService;

	@PostMapping("/conta")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void vincularConta(@PathVariable UUID pacienteId, @RequestBody @Valid VinculoContaRequest requisicao,
			Principal administrativo) {
		acessoAoCadastroService.vincularConta(pacienteId, requisicao.instituicaoId(), requisicao.login(),
				administrativo.getName());
	}

	@GetMapping("/representantes")
	public List<RepresentacaoResponse> representantes(@PathVariable UUID pacienteId, @RequestParam UUID instituicaoId,
			Principal administrativo) {
		return acessoAoCadastroService.listarRepresentacoes(pacienteId, instituicaoId, administrativo.getName());
	}

	@PostMapping("/representantes")
	@ResponseStatus(HttpStatus.CREATED)
	public RepresentacaoResponse registrarRepresentante(@PathVariable UUID pacienteId,
			@RequestBody @Valid RepresentacaoRequest requisicao, Principal administrativo) {
		return acessoAoCadastroService.registrarRepresentacao(pacienteId, requisicao, administrativo.getName());
	}

	@PostMapping("/representantes/{representacaoId}/encerramento")
	public RepresentacaoResponse encerrarRepresentacao(@PathVariable UUID pacienteId,
			@PathVariable UUID representacaoId, @RequestBody @Valid InstituicaoDoAdministrativoRequest requisicao,
			Principal administrativo) {
		return acessoAoCadastroService.encerrarRepresentacao(pacienteId, representacaoId, requisicao.instituicaoId(),
				administrativo.getName());
	}
}
