package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.core.env.Environment;

/** Número inteiro clínico (ex.: frequência cardíaca) gravado cifrado em coluna de texto (RNF-004). */
@Converter
public class InteiroCifrado implements AttributeConverter<Integer, String> {

	private final CifraDeDadosClinicos cifra;

	public InteiroCifrado(Environment ambiente) {
		this.cifra = CifraDeDadosClinicos.doAmbiente(ambiente);
	}

	@Override
	public String convertToDatabaseColumn(Integer valor) {
		return valor == null ? null : cifra.cifrar(valor.toString());
	}

	@Override
	public Integer convertToEntityAttribute(String coluna) {
		return coluna == null ? null : Integer.valueOf(cifra.decifrar(coluna));
	}
}
