package com.sigh.sigh.project.triagem.dto;

import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SinaisVitaisDTO(
		@Pattern(regexp = "\\d{2,3}/\\d{2,3}", message = "use o formato sistólica/diastólica, por exemplo 120/80")
		String pressaoArterial,
		@Positive Integer frequenciaCardiaca,
		@Positive Integer frequenciaRespiratoria,
		@Positive @Digits(integer = 3, fraction = 1) BigDecimal temperatura,
		@DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 1) BigDecimal saturacaoOxigenio) {

	public SinaisVitais paraEntidade() {
		SinaisVitais sinaisVitais = new SinaisVitais();
		sinaisVitais.setPressaoArterial(pressaoArterial);
		sinaisVitais.setFrequenciaCardiaca(frequenciaCardiaca);
		sinaisVitais.setFrequenciaRespiratoria(frequenciaRespiratoria);
		sinaisVitais.setTemperatura(temperatura);
		sinaisVitais.setSaturacaoOxigenio(saturacaoOxigenio);
		return sinaisVitais;
	}

	public static SinaisVitaisDTO de(SinaisVitais sinaisVitais) {
		if (sinaisVitais == null) {
			return null;
		}
		return new SinaisVitaisDTO(
				sinaisVitais.getPressaoArterial(),
				sinaisVitais.getFrequenciaCardiaca(),
				sinaisVitais.getFrequenciaRespiratoria(),
				sinaisVitais.getTemperatura(),
				sinaisVitais.getSaturacaoOxigenio());
	}
}
