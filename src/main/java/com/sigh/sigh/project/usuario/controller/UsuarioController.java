package com.sigh.sigh.project.usuario.controller;

import com.sigh.sigh.project.usuario.dto.UsuarioLogadoDTO;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

	private final UsuarioService usuarioService;

	@GetMapping("/eu")
	@PreAuthorize("hasAnyRole('PACIENTE', 'ENFERMEIRO', 'MEDICO', 'ADMINISTRATIVO', 'RECEPCAO', 'AUDITOR_GESTOR')")
	public UsuarioLogadoDTO eu(Principal usuario) {
		return usuarioService.logado(usuario.getName());
	}
}
