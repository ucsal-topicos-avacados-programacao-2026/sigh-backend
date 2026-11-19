package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import jakarta.validation.constraints.NotNull;

public record ConfirmacaoRiscoRequestDTO(@NotNull CorRisco cor) {
}
