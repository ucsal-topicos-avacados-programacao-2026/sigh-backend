package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.usuario.entity.Perfil;

import java.util.UUID;

/** Pedido de sugestão da IA para uma triagem, feito em nome de quem a abriu ou a atualizou. */
record SugestaoSolicitada(UUID triagemId, String login, Perfil perfil, String finalidade) {
}
