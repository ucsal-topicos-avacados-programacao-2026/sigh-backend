package com.sigh.sigh.project.usuario.controller;

import com.sigh.sigh.project.usuario.dto.CadastroSegundoFatorResponseDTO;
import com.sigh.sigh.project.usuario.dto.CodigoSegundoFatorRequestDTO;
import com.sigh.sigh.project.usuario.dto.LoginRequestDTO;
import com.sigh.sigh.project.usuario.dto.TokenResponseDTO;
import com.sigh.sigh.project.usuario.service.AutenticacaoService;
import com.sigh.sigh.project.usuario.service.SegundoFatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

	private final AutenticacaoService autenticacaoService;
	private final SegundoFatorService segundoFatorService;

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
}
