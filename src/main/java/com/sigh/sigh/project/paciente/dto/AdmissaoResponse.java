package com.sigh.sigh.project.paciente.dto;

import java.util.UUID;

/**
 * @param novoCadastro false quando o CPF já existia na rede e o atendimento foi
 *                     vinculado ao prontuário existente (RN-004)
 */
public record AdmissaoResponse(UUID pacienteId, UUID prontuarioId, UUID atendimentoId, boolean novoCadastro) {
}
