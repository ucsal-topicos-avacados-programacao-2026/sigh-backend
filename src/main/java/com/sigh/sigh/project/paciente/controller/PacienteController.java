package com.sigh.sigh.project.paciente.controller;

import com.sigh.sigh.project.comum.erro.ViolacaoDeUnicidade;
import com.sigh.sigh.project.paciente.dto.AdmissaoRequest;
import com.sigh.sigh.project.paciente.dto.AdmissaoResponse;
import com.sigh.sigh.project.paciente.dto.BuscaPacienteRequest;
import com.sigh.sigh.project.paciente.dto.PacienteResumoResponse;
import com.sigh.sigh.project.paciente.service.AdmissaoPacienteService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

	private final AdmissaoPacienteService admissaoService;

	public PacienteController(AdmissaoPacienteService admissaoService) {
		this.admissaoService = admissaoService;
	}

	@PostMapping("/busca")
	@PreAuthorize("hasRole('ADMINISTRATIVO')")
	public PacienteResumoResponse buscarPorCpf(@Valid @RequestBody BuscaPacienteRequest requisicao,
			Authentication autenticacao) {
		return admissaoService.buscarPorCpf(requisicao, autenticacao.getName());
	}

	@PostMapping("/admissoes")
	@PreAuthorize("hasRole('ADMINISTRATIVO')")
	@ResponseStatus(HttpStatus.CREATED)
	public AdmissaoResponse admitir(@Valid @RequestBody AdmissaoRequest requisicao, Authentication autenticacao) {
		try {
			return admissaoService.admitir(requisicao, autenticacao.getName());
		} catch (DataIntegrityViolationException e) {
			if (!ViolacaoDeUnicidade.eh(e)) {
				throw e;
			}
			// Outra recepção cadastrou o mesmo CPF ao mesmo tempo. Uma nova tentativa, em nova
			// transação, encontra o cadastro e vincula o atendimento ao prontuário existente (RN-004).
			return admissaoService.admitir(requisicao, autenticacao.getName());
		}
	}
}
