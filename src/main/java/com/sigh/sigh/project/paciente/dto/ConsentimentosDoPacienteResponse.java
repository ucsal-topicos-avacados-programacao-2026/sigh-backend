package com.sigh.sigh.project.paciente.dto;

import java.util.List;

/**
 * @param vigente   decisão atual sobre o compartilhamento amplo; null se o paciente nunca se manifestou
 * @param historico todas as versões, da mais antiga para a mais recente (nada é apagado)
 */
public record ConsentimentosDoPacienteResponse(ConsentimentoResponse vigente, List<ConsentimentoResponse> historico) {
}
