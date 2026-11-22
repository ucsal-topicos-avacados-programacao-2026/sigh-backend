package com.sigh.sigh.project.usuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record CriarUsuarioRequest(
		@NotBlank(message = "Login é obrigatório")
		@Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,39}$",
				message = "Use de 3 a 40 caracteres: letras minúsculas, números, ponto, hífen ou sublinhado") String login,
		@NotBlank(message = "Nome é obrigatório")
		@Size(max = 150, message = "Nome deve ter no máximo 150 caracteres") String nome,
		@NotEmpty(message = "Informe ao menos um perfil") Set<Perfil> perfis,
		@Valid List<VinculoRequest> vinculos) {
}
