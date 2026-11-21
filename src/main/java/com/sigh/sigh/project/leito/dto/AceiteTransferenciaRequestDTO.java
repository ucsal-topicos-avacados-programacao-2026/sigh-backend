package com.sigh.sigh.project.leito.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AceiteTransferenciaRequestDTO(@NotNull UUID leitoId) {
}
