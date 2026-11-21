package com.sigh.sigh.project.triagem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Sinais vitais medidos pelo enfermeiro numa triagem que já nasceu na recepção. */
public record SinaisVitaisRequestDTO(@NotNull @Valid SinaisVitaisDTO sinaisVitais) {
}
