package com.sigh.sigh.project.auditoria.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

public record FiltroAuditoriaDTO(
		UUID instituicaoId,
		Boolean emergencia,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
		@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

	public PeriodoDTO periodo() {
		return new PeriodoDTO(inicio, fim);
	}
}
