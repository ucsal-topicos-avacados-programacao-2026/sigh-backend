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

	private static final String AVISO_EM_GERACAO =
			"Resumo em geração pela IA. Consulte de novo em instantes; os registros originais já estão disponíveis.";

	private static final String AVISO_DE_FALHA =
			"A IA não gerou o resumo. Siga pelos registros originais ou peça um novo resumo.";

	private static final String AVISO_DE_RESTRICAO = "Resumo oculto: ele usa informação que o paciente restringiu"
			+ " ou deixou de compartilhar depois da geração. Gere um novo resumo.";

	/** Resumo cujas fontes deixaram de ser visíveis: o texto e as fontes não são devolvidos (RF-009). */
	public static ResumoHistoricoResponseDTO oculto(ResumoHistoricoIA resumo) {
		return new ResumoHistoricoResponseDTO(resumo.getId(), resumo.getAtendimento().getId(), resumo.getStatus(), null,
				resumo.getGeradoEm(), resumo.getModeloVersao(), Set.of(), AVISO_DE_RESTRICAO);
	}

	/** Resposta do pedido de resumo: em geração e sem texto. */
	public static ResumoHistoricoResponseDTO emGeracao(ResumoHistoricoIA resumo) {
		return new ResumoHistoricoResponseDTO(resumo.getId(), resumo.getAtendimento().getId(), StatusResumo.SOLICITADO,
				null, null, null, resumo.getFontes().stream().map(RegistroClinico::getId).collect(Collectors.toSet()),
				AVISO_EM_GERACAO);
	}

	public static ResumoHistoricoResponseDTO de(ResumoHistoricoIA resumo) {
		return new ResumoHistoricoResponseDTO(
				resumo.getId(),
				resumo.getAtendimento().getId(),
				resumo.getStatus(),
				resumo.getTexto(),
				resumo.getGeradoEm(),
				resumo.getModeloVersao(),
				resumo.getFontes().stream().map(RegistroClinico::getId).collect(Collectors.toSet()),
				switch (resumo.getStatus()) {
					case SOLICITADO -> AVISO_EM_GERACAO;
					case FALHOU -> AVISO_DE_FALHA;
					case GERADO, VALIDADO -> AVISO_DE_IA;
				});
	}
}
