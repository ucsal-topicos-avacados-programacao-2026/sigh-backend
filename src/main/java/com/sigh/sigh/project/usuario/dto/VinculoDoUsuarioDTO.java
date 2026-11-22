package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record VinculoDoUsuarioDTO(UUID id, UUID instituicaoId, String instituicaoNome, Set<Perfil> perfis,
		String registroProfissional, LocalDateTime inicioEm, LocalDateTime fimEm, boolean ativo) {

	public static VinculoDoUsuarioDTO de(VinculoInstitucional vinculo, LocalDateTime agora) {
		return new VinculoDoUsuarioDTO(vinculo.getId(), vinculo.getInstituicao().getId(),
				vinculo.getInstituicao().getNome(), new TreeSet<>(vinculo.getPerfisHabilitados()),
				vinculo.getRegistroProfissional(), vinculo.getInicioEm(), vinculo.getFimEm(), vinculo.estaAtivo(agora));
	}
}
