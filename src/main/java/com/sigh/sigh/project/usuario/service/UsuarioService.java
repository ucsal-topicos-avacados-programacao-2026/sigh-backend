package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;

	@Transactional(readOnly = true)
	public Usuario buscarPorLogin(String login) {
		return usuarioRepository.findByLogin(login)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
	}
}
