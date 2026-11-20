package com.sigh.sigh.project.ia.dto;

import com.sigh.sigh.project.ia.entity.ResumoHistoricoIA;
import com.sigh.sigh.project.ia.entity.StatusResumo;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ResumoHistoricoResponseDTO(
		UUID id,
		UUID atendimentoId,
		StatusResumo status,
		String texto,
		LocalDateTime geradoEm,
		String modeloVersao,
		Set<UUID> registrosDeOrigem,
		String aviso) {

	private static final String AVISO_DE_IA =
			"Resumo gerado por IA, sujeito à validação profissional. Os registros originais continuam disponíveis.";

	public static ResumoHistoricoResponseDTO de(ResumoHistoricoIA resumo) {
		return new ResumoHistoricoResponseDTO(
				resumo.getId(),
				resumo.getAtendimento().getId(),
				resumo.getStatus(),
				resumo.getTexto(),
				resumo.getGeradoEm(),
				resumo.getModeloVersao(),
				resumo.getFontes().stream().map(RegistroClinico::getId).collect(Collectors.toSet()),
				AVISO_DE_IA);
	}
}
