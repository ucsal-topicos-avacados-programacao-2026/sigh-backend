package com.sigh.sigh.project.painel.dto;

import com.sigh.sigh.project.triagem.entity.CorRisco;

import java.time.LocalDateTime;

/** Resultado de consulta: a cor e a chegada de quem está na fila do médico, sem identificar o paciente. */
public record ChegadaNaFila(CorRisco cor, LocalDateTime chegadaEm) {
}
