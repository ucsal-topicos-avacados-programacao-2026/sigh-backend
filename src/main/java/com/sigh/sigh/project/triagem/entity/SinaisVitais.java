package com.sigh.sigh.project.triagem.entity;

import com.sigh.sigh.project.comum.cripto.DecimalCifrado;
import com.sigh.sigh.project.comum.cripto.InteiroCifrado;
import com.sigh.sigh.project.comum.cripto.TextoCifrado;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
public class SinaisVitais {

	@Convert(converter = TextoCifrado.class)
	private String pressaoArterial;
	@Convert(converter = InteiroCifrado.class)
	private Integer frequenciaCardiaca;
	@Convert(converter = InteiroCifrado.class)
	private Integer frequenciaRespiratoria;
	@Convert(converter = DecimalCifrado.class)
	private BigDecimal temperatura;
	@Convert(converter = DecimalCifrado.class)
	private BigDecimal saturacaoOxigenio;
}
