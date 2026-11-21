package com.sigh.sigh.project.paciente.cpf;

import java.util.regex.Pattern;

public final class Cpf {

	private static final Pattern FORMATO = Pattern.compile("^\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}$");

	private Cpf() {
	}

	public static boolean ehValido(String valor) {
		if (valor == null) {
			return false;
		}
		String texto = valor.strip();
		if (!FORMATO.matcher(texto).matches()) {
			return false;
		}
		String digitos = texto.replace(".", "").replace("-", "");
		if (digitos.chars().distinct().count() == 1) {
			return false;
		}
		return digitoVerificador(digitos, 9) == digitos.charAt(9) - '0'
				&& digitoVerificador(digitos, 10) == digitos.charAt(10) - '0';
	}

	public static String normalizar(String valor) {
		if (!ehValido(valor)) {
			throw new IllegalArgumentException("CPF inválido");
		}
		return valor.strip().replace(".", "").replace("-", "");
	}

	private static int digitoVerificador(String digitos, int quantidade) {
		int soma = 0;
		for (int i = 0; i < quantidade; i++) {
			soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
		}
		int resto = soma % 11;
		return resto < 2 ? 0 : 11 - resto;
	}
}
