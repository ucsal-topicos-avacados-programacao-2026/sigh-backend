package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** O que o paciente quer restringir (RF-009): uma categoria de informação ou um registro específico. */
public record AlvoDaRestricaoRequest(@Size(max = 50) String categoria, UUID registroId) {
}
