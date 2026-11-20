package com.sigh.sigh.project.leito.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecusaTransferenciaRequestDTO(@NotBlank @Size(max = 500) String motivo) {
}
