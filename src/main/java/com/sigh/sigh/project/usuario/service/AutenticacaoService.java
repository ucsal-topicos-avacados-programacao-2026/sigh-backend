package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.CredenciaisInvalidasException;
import com.sigh.sigh.project.usuario.dto.EtapaAutenticacao;
import com.sigh.sigh.project.usuario.dto.LoginRequestDTO;
import com.sigh.sigh.project.usuario.dto.TokenResponseDTO;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AutenticacaoService {

	private static final Set<Perfil> PERFIS_COM_SEGUNDO_FATOR =
			EnumSet.of(Perfil.MEDICO, Perfil.ENFERMEIRO, Perfil.AUDITOR_GESTOR);

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder codificadorDeSenha;
	private final TokenService tokenService;

	@Transactional(readOnly = true)
	public TokenResponseDTO entrar(LoginRequestDTO dados) {
		Usuario usuario = usuarioRepository.findByLogin(dados.login())
				.filter(encontrado -> codificadorDeSenha.matches(dados.senha(), encontrado.getSenhaHash()))
				.orElseThrow(() -> new CredenciaisInvalidasException("Login ou senha inválidos."));
		if (!exigeSegundoFator(usuario)) {
			return new TokenResponseDTO(tokenService.gerarTokenDeAcesso(usuario), EtapaAutenticacao.ACESSO_LIBERADO);
		}
		EtapaAutenticacao proximaEtapa = usuario.isMfaHabilitado()
				? EtapaAutenticacao.VALIDAR_SEGUNDO_FATOR
				: EtapaAutenticacao.CADASTRAR_SEGUNDO_FATOR;
		return new TokenResponseDTO(tokenService.gerarTokenDeSegundoFator(usuario), proximaEtapa);
	}

	private boolean exigeSegundoFator(Usuario usuario) {
		return !Collections.disjoint(usuario.getPerfis(), PERFIS_COM_SEGUNDO_FATOR);
	}
}
