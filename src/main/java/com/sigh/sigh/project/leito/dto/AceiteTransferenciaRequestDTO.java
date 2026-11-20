package com.sigh.sigh.project.leito.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** O leito livre do destino, escolhido pelo administrativo do destino. */
public record AceiteTransferenciaRequestDTO(@NotNull UUID leitoId) {
}
