package com.sigh.sigh.project.usuario.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GeradorDeSenha {

	private static final String MAIUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
	private static final String DIGITOS = "23456789";
	private static final String TODOS = MAIUSCULAS + MINUSCULAS + DIGITOS;
	private static final int TAMANHO = 16;

	private final SecureRandom aleatorio = new SecureRandom();

	public String gerar() {
		List<Character> caracteres = new ArrayList<>();
		caracteres.add(sortear(MAIUSCULAS));
		caracteres.add(sortear(MINUSCULAS));
		caracteres.add(sortear(DIGITOS));
		while (caracteres.size() < TAMANHO) {
			caracteres.add(sortear(TODOS));
		}
		Collections.shuffle(caracteres, aleatorio);
		StringBuilder senha = new StringBuilder();
		caracteres.forEach(senha::append);
		return senha.toString();
	}

	private char sortear(String alfabeto) {
		return alfabeto.charAt(aleatorio.nextInt(alfabeto.length()));
	}
}
