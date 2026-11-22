package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record UsuarioDetalheDTO(UUID id, String login, String nome, Set<Perfil> perfis, boolean ativo,
		boolean mfaHabilitado, boolean senhaTemporaria, List<VinculoDoUsuarioDTO> vinculos) {

	public static UsuarioDetalheDTO de(Usuario usuario, List<VinculoDoUsuarioDTO> vinculos) {
		return new UsuarioDetalheDTO(usuario.getId(), usuario.getLogin(), usuario.getNome(),
				new TreeSet<>(usuario.getPerfis()), usuario.isAtivo(), usuario.isMfaHabilitado(),
				usuario.isSenhaTemporaria(), vinculos);
	}
}
