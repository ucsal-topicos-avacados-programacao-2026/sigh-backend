package com.sigh.sigh.project.leito.dto;

import java.util.UUID;

/** Só os ids de uma solicitação: lidos sem carregar a entidade, para conferir o vínculo antes de travar. */
public record ReferenciasDaSolicitacao(UUID internacaoId, UUID instituicaoOrigemId, UUID instituicaoDestinoId) {
}
