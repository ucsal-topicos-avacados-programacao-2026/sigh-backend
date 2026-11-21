package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UsuarioLogadoDTO(String login, String nome, Set<Perfil> perfis, List<VinculoAtivo> vinculos) {

	public record VinculoAtivo(UUID instituicaoId, String instituicaoNome, Set<Perfil> perfis) {
	}
}
