package com.sigh.sigh.project.triagem.dto;

import java.util.Set;
import java.util.UUID;

public record CasoTriagemDTO(UUID triagemId, String descricao, Set<UUID> registrosUsados) {
}
