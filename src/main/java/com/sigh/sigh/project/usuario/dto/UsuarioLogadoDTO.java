package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quem está logado, para o front-end montar o menu por perfil e o seletor de instituição (FE-02, RF-012).
 * Os vínculos são só os ativos agora, com os perfis habilitados em cada instituição.
 */
public record UsuarioLogadoDTO(String login, String nome, Set<Perfil> perfis, List<VinculoAtivo> vinculos) {

	public record VinculoAtivo(UUID instituicaoId, String instituicaoNome, Set<Perfil> perfis) {
	}
}
