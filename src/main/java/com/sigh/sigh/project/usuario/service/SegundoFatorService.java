package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.CredenciaisInvalidasException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.config.SegundoFatorProperties;
import com.sigh.sigh.project.usuario.dto.CadastroSegundoFatorResponseDTO;
import com.sigh.sigh.project.usuario.dto.EtapaAutenticacao;
import com.sigh.sigh.project.usuario.dto.TokenResponseDTO;
import com.sigh.sigh.project.usuario.entity.Usuario;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SegundoFatorService {

	private final UsuarioService usuarioService;
	private final TokenService tokenService;
	private final SecretGenerator geradorDeSegredo;
	private final CodeVerifier verificadorDeCodigo;
	private final SegundoFatorProperties segundoFatorProperties;

	@Transactional
	public CadastroSegundoFatorResponseDTO cadastrar(String login) {
		Usuario usuario = usuarioService.buscarPorLogin(login);
		if (usuario.isMfaHabilitado()) {
			throw new RegraNegocioException("O segundo fator já foi cadastrado.");
		}
		usuario.setMfaSegredo(geradorDeSegredo.generate());
		return new CadastroSegundoFatorResponseDTO(montarUriDoAutenticador(usuario));
	}

	@Transactional
	public TokenResponseDTO validar(String login, String codigo) {
		Usuario usuario = usuarioService.buscarPorLogin(login);
		if (usuario.getMfaSegredo() == null) {
			throw new RegraNegocioException("Cadastre o segundo fator antes de validar o código.");
		}
		if (!verificadorDeCodigo.isValidCode(usuario.getMfaSegredo(), codigo)) {
			throw new CredenciaisInvalidasException("Código do segundo fator inválido.");
		}
		usuario.setMfaHabilitado(true);
		return new TokenResponseDTO(tokenService.gerarTokenDeAcesso(usuario), EtapaAutenticacao.ACESSO_LIBERADO);
	}

	private String montarUriDoAutenticador(Usuario usuario) {
		return new QrData.Builder()
				.label(usuario.getLogin())
				.secret(usuario.getMfaSegredo())
				.issuer(segundoFatorProperties.emissor())
				.build()
				.getUri();
	}
}
