package com.sigh.sigh.project.triagem.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
public class SinaisVitais {

	private String pressaoArterial;
	private Integer frequenciaCardiaca;
	private Integer frequenciaRespiratoria;
	private BigDecimal temperatura;
	private BigDecimal saturacaoOxigenio;
}
