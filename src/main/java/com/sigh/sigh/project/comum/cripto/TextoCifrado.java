package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.core.env.Environment;

/** Coluna de texto clínico gravada cifrada (RNF-004); a entidade enxerga o texto claro. */
@Converter
public class TextoCifrado implements AttributeConverter<String, String> {

	private final CifraDeDadosClinicos cifra;

	public TextoCifrado(Environment ambiente) {
		this.cifra = CifraDeDadosClinicos.doAmbiente(ambiente);
	}

	@Override
	public String convertToDatabaseColumn(String texto) {
		return cifra.cifrar(texto);
	}

	@Override
	public String convertToEntityAttribute(String coluna) {
		return cifra.decifrar(coluna);
	}
}
