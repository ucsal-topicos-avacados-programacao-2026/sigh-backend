package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.SituacaoSugestao;

/**
 * O que a recepção vê da pré-classificação: só o andamento, nunca a cor nem o histórico (RF-012).
 *
 * @param situacao nulo quando o atendimento não tem triagem aguardando confirmação
 */
public record PreClassificacaoResponseDTO(SituacaoSugestao situacao) {
}
