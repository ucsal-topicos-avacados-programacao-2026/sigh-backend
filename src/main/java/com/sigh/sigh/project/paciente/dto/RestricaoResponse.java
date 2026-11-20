package com.sigh.sigh.project.paciente.dto;

import com.sigh.sigh.project.paciente.entity.RestricaoAcesso;

import java.time.LocalDateTime;
import java.util.UUID;

public record RestricaoResponse(UUID id, String categoria, UUID registroId, LocalDateTime inicioEm,
		LocalDateTime fimEm, boolean vigente, String definidaPor, String encerradaPor) {

	public static RestricaoResponse de(RestricaoAcesso restricao, LocalDateTime agora) {
		boolean vigente = !restricao.getInicioEm().isAfter(agora)
				&& (restricao.getFimEm() == null || restricao.getFimEm().isAfter(agora));
		return new RestricaoResponse(restricao.getId(), restricao.getCategoriaAlvo(),
				restricao.getRegistroAlvo() == null ? null : restricao.getRegistroAlvo().getId(),
				restricao.getInicioEm(), restricao.getFimEm(), vigente, restricao.getDefinidaPor().getNome(),
				restricao.getEncerradaPor() == null ? null : restricao.getEncerradaPor().getNome());
	}
}
