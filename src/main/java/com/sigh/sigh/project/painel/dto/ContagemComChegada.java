package com.sigh.sigh.project.painel.dto;

import java.time.LocalDateTime;

/** Resultado de consulta: quantos e a chegada mais antiga entre eles (nula sem nenhum). */
public record ContagemComChegada(long quantidade, LocalDateTime chegadaMaisAntiga) {
}
