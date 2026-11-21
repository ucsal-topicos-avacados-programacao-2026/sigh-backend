package com.sigh.sigh.project.auditoria.dto;

import com.sigh.sigh.project.auditoria.entity.ConclusaoAnalise;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnaliseAuditoriaRequestDTO(
		@NotNull TipoAnalise tipo,
		@NotNull ConclusaoAnalise conclusao,
		@NotBlank String descricao) {
}
