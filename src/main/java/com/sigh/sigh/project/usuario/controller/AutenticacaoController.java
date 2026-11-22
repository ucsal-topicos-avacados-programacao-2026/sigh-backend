package com.sigh.sigh.project.usuario.controller;

import com.sigh.sigh.project.usuario.dto.AlteracaoDeSenhaRequest;
import com.sigh.sigh.project.usuario.dto.CadastroSegundoFatorResponseDTO;
import com.sigh.sigh.project.usuario.dto.CodigoSegundoFatorRequestDTO;
import com.sigh.sigh.project.usuario.dto.LoginRequestDTO;
import com.sigh.sigh.project.usuario.dto.NovaSenhaRequest;
import com.sigh.sigh.project.usuario.dto.TokenResponseDTO;
import com.sigh.sigh.project.usuario.service.AutenticacaoService;
import com.sigh.sigh.project.usuario.service.SegundoFatorService;
import com.sigh.sigh.project.usuario.service.SenhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

	private final AutenticacaoService autenticacaoService;
	private final SegundoFatorService segundoFatorService;
	private final SenhaService senhaService;

	@PostMapping("/login")
	public TokenResponseDTO entrar(@RequestBody @Valid LoginRequestDTO dados) {
		return autenticacaoService.entrar(dados);
	}

	@PostMapping("/segundo-fator/cadastro")
	public CadastroSegundoFatorResponseDTO cadastrarSegundoFator(Principal usuario) {
		return segundoFatorService.cadastrar(usuario.getName());
	}

	@PostMapping("/segundo-fator/validacao")
	public TokenResponseDTO validarSegundoFator(@RequestBody @Valid CodigoSegundoFatorRequestDTO dados,
			Principal usuario) {
		return segundoFatorService.validar(usuario.getName(), dados.codigo());
	}

	@PostMapping("/senha/troca-inicial")
	public TokenResponseDTO trocarSenhaInicial(@RequestBody @Valid NovaSenhaRequest dados, Principal usuario) {
		return senhaService.trocarNoPrimeiroAcesso(usuario.getName(), dados.novaSenha());
	}

	@PostMapping("/senha/alteracao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('PACIENTE', 'ENFERMEIRO', 'MEDICO', 'ADMINISTRATIVO', 'RECEPCAO', 'AUDITOR_GESTOR')")
	public void alterarSenha(@RequestBody @Valid AlteracaoDeSenhaRequest dados, Principal usuario) {
		senhaService.alterar(usuario.getName(), dados.senhaAtual(), dados.novaSenha());
	}
}
