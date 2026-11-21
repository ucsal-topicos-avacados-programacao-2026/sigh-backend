package com.sigh.sigh.project.leito.dto;

import java.util.UUID;

public record ReferenciasDaSolicitacao(UUID internacaoId, UUID instituicaoOrigemId, UUID instituicaoDestinoId) {
}
