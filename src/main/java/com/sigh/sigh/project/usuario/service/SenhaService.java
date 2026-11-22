package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.usuario.dto.TokenResponseDTO;
import com.sigh.sigh.project.usuario.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SenhaService {

	private final UsuarioService usuarioService;
	private final AutenticacaoService autenticacaoService;
	private final PasswordEncoder codificadorDeSenha;

	@Transactional
	public TokenResponseDTO trocarNoPrimeiroAcesso(String login, String novaSenha) {
		Usuario usuario = usuarioService.buscarPorLogin(login);
		if (!usuario.isSenhaTemporaria()) {
			throw new RegraNegocioException("A senha já foi trocada.");
		}
		definir(usuario, novaSenha);
		usuario.setSenhaTemporaria(false);
		return autenticacaoService.proximaEtapa(usuario);
	}

	@Transactional
	public void alterar(String login, String senhaAtual, String novaSenha) {
		Usuario usuario = usuarioService.buscarPorLogin(login);
		if (!codificadorDeSenha.matches(senhaAtual, usuario.getSenhaHash())) {
			throw new RegraNegocioException("A senha atual está incorreta.");
		}
		definir(usuario, novaSenha);
	}

	private void definir(Usuario usuario, String novaSenha) {
		boolean temLetra = novaSenha.chars().anyMatch(Character::isLetter);
		boolean temDigito = novaSenha.chars().anyMatch(Character::isDigit);
		if (!temLetra || !temDigito) {
			throw new RegraNegocioException("A senha precisa ter letras e números.");
		}
		if (novaSenha.equalsIgnoreCase(usuario.getLogin())) {
			throw new RegraNegocioException("A senha não pode ser igual ao login.");
		}
		if (codificadorDeSenha.matches(novaSenha, usuario.getSenhaHash())) {
			throw new RegraNegocioException("A nova senha deve ser diferente da atual.");
		}
		usuario.setSenhaHash(codificadorDeSenha.encode(novaSenha));
	}
}
