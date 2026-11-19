package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.StatusLeito;
import jakarta.validation.constraints.NotNull;

public record AlteracaoStatusLeitoRequestDTO(@NotNull StatusLeito status) {
}
