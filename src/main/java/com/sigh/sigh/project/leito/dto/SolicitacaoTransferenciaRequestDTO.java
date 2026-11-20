package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SolicitacaoTransferenciaRequestDTO(@NotNull UUID instituicaoDestinoId, @NotNull CategoriaLeito categoria) {
}
