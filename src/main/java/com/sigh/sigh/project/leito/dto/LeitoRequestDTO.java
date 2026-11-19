package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LeitoRequestDTO(
		@NotNull UUID instituicaoId,
		@NotBlank @Size(max = 20) String codigo,
		@NotBlank @Size(max = 50) String setor,
		@NotNull CategoriaLeito categoria) {
}
