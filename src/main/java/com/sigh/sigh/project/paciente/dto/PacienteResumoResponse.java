package com.sigh.sigh.project.paciente.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PacienteResumoResponse(UUID id, String nome, LocalDate dataNascimento) {
}
