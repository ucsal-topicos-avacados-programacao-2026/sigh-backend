package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.util.UUID;

record SugestaoSolicitada(UUID triagemId, String login, Perfil perfil, String finalidade) {
}
