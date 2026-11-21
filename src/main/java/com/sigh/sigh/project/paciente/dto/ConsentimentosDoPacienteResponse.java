package com.sigh.sigh.project.paciente.dto;

import java.util.List;

public record ConsentimentosDoPacienteResponse(ConsentimentoResponse vigente, List<ConsentimentoResponse> historico) {
}
