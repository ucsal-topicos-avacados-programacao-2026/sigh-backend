package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.config.JwtProperties;
import com.sigh.sigh.project.usuario.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

	public static final String CLAIM_AUTORIZACOES = "autorizacoes";
	public static final String SEGUNDO_FATOR_PENDENTE = "SEGUNDO_FATOR_PENDENTE";
	public static final String SENHA_PENDENTE = "SENHA_PENDENTE";
	private static final String PREFIXO_PERFIL = "ROLE_";

	private final JwtEncoder codificadorDeToken;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public String gerarTokenDeAcesso(Usuario usuario) {
		List<String> perfis = usuario.getPerfis().stream()
				.map(perfil -> PREFIXO_PERFIL + perfil.name())
				.toList();
		return gerar(usuario.getLogin(), perfis, jwtProperties.validade());
	}

	public String gerarTokenDeSegundoFator(Usuario usuario) {
		return gerar(usuario.getLogin(), List.of(SEGUNDO_FATOR_PENDENTE), jwtProperties.validadeSegundoFatorPendente());
	}

	public String gerarTokenDeSenha(Usuario usuario) {
		return gerar(usuario.getLogin(), List.of(SENHA_PENDENTE), jwtProperties.validadeSegundoFatorPendente());
	}

	private String gerar(String login, List<String> autorizacoes, Duration validade) {
		Instant agora = clock.instant();
		JwtClaimsSet dados = JwtClaimsSet.builder()
				.subject(login)
				.issuedAt(agora)
				.expiresAt(agora.plus(validade))
				.claim(CLAIM_AUTORIZACOES, autorizacoes)
				.build();
		JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
		return codificadorDeToken.encode(JwtEncoderParameters.from(cabecalho, dados)).getTokenValue();
	}
}
