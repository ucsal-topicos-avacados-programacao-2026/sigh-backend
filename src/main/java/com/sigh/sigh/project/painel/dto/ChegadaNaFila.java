package com.sigh.sigh.project.painel.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;

public record ChegadaNaFila(CorRisco cor, LocalDateTime chegadaEm) {
}
