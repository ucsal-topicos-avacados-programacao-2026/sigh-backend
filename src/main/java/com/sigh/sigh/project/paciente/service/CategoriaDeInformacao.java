package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;

import java.util.Locale;

final class CategoriaDeInformacao {

	private CategoriaDeInformacao() {
	}

	static String apresentavel(String categoria) {
		return categoria.replaceAll("\\s+", " ").strip();
	}

	static boolean mesma(String uma, String outra) {
		return uma != null && outra != null && chave(uma).equals(chave(outra));
	}

	static String chave(String categoria) {
		StringBuilder chave = new StringBuilder();
		for (char c : apresentavel(categoria).toCharArray()) {
			int indice = RegistroClinicoRepository.ACENTOS.indexOf(c);
			chave.append(indice >= 0 ? RegistroClinicoRepository.SEM_ACENTOS.charAt(indice) : c);
		}
		return chave.toString().toLowerCase(Locale.ROOT);
	}
}
