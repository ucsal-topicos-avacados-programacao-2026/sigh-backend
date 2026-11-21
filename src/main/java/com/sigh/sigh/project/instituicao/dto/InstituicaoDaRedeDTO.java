package com.sigh.sigh.project.instituicao.dto;

import java.util.UUID;

/** Instituição credenciada na rede: destino de uma autorização adicional (RF-009) ou de uma transferência (RF-006). */
public record InstituicaoDaRedeDTO(UUID id, String nome) {
}
