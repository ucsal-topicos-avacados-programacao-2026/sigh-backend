package com.sigh.sigh.project.leito.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.StatusLeito;

import java.util.UUID;

public record FiltroLeitoDTO(UUID instituicaoId, CategoriaLeito categoria, StatusLeito status) {
}
