package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroClinicoRequestDTO(
		@NotNull TipoRegistro tipo,
		@NotBlank String conteudo,
		@NotBlank @Size(max = 50) String categoriaInformacao,
		@Pattern(regexp = "[A-Z][0-9]{2}(\\.[0-9]{1,2})?", message = "deve seguir o formato do CID-10, como J45.9")
		String codigoCID10) {
}
