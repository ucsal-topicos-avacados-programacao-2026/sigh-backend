package com.sigh.sigh.project.instituicao.dto;

import java.util.UUID;

/** Instituição credenciada na rede, como o paciente a escolhe numa autorização adicional (RF-009). */
public record InstituicaoDaRedeDTO(UUID id, String nome) {
}
