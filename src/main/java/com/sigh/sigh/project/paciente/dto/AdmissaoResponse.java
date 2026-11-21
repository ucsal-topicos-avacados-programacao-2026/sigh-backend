package com.sigh.sigh.project.paciente.dto;

import java.util.UUID;

public record AdmissaoResponse(UUID pacienteId, UUID prontuarioId, UUID atendimentoId, boolean novoCadastro) {
}
