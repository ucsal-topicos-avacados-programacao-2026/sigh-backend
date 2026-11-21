package com.sigh.sigh.project.triagem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SinaisVitaisRequestDTO(@NotNull @Valid SinaisVitaisDTO sinaisVitais) {
}
