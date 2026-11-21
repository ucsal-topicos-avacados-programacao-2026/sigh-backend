package com.sigh.sigh.project.painel.dto;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.StatusLeito;

public record LeitosDoPainelDTO(CategoriaLeito categoria, StatusLeito status, long quantidade) {
}
