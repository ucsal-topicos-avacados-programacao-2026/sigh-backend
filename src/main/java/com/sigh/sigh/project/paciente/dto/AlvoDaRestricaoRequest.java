package com.sigh.sigh.project.paciente.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AlvoDaRestricaoRequest(@Size(max = 50) String categoria, UUID registroId) {
}
