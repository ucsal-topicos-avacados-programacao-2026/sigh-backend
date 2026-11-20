package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;

/** Número decimal clínico (ex.: temperatura) gravado cifrado em coluna de texto (RNF-004). */
@Converter
public class DecimalCifrado implements AttributeConverter<BigDecimal, String> {

	private final CifraDeDadosClinicos cifra;

	public DecimalCifrado(Environment ambiente) {
		this.cifra = CifraDeDadosClinicos.doAmbiente(ambiente);
	}

	@Override
	public String convertToDatabaseColumn(BigDecimal valor) {
		return valor == null ? null : cifra.cifrar(valor.toPlainString());
	}

	@Override
	public BigDecimal convertToEntityAttribute(String coluna) {
		return coluna == null ? null : new BigDecimal(cifra.decifrar(coluna));
	}
}
