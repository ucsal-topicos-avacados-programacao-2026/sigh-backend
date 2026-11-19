package com.sigh.sigh.project.leito.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferenciaRequestDTO(@NotNull UUID leitoDestinoId) {
}
