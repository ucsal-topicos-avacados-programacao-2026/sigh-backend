package com.sigh.sigh.project.auditoria.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PeriodoDTO(
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

	public boolean terminaAntesDeComecar() {
		return fim.isBefore(inicio);
	}

	public LocalDateTime inicioDoPrimeiroDia() {
		return inicio.atStartOfDay();
	}

	public LocalDateTime inicioDoDiaSeguinteAoFim() {
		return fim.plusDays(1).atStartOfDay();
	}
}
