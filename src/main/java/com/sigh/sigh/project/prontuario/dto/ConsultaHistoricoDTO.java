package com.sigh.sigh.project.prontuario.dto;

import com.sigh.sigh.project.usuario.entity.Perfil;

public record ConsultaHistoricoDTO(String loginProfissional, Perfil perfil, String finalidade, int limiteDeRegistros) {
}
