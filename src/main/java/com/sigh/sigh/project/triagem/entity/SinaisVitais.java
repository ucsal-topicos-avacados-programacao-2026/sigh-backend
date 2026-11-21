package com.sigh.sigh.project.triagem.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
public class SinaisVitais {

	@Transient
	@Cifrado(gravadoEm = "pressaoArterialCifrado")
	private String pressaoArterial;
	@Column(name = "pressao_arterial")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String pressaoArterialCifrado;
	@Transient
	@Cifrado(gravadoEm = "frequenciaCardiacaCifrado")
	private Integer frequenciaCardiaca;
	@Column(name = "frequencia_cardiaca")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String frequenciaCardiacaCifrado;
	@Transient
	@Cifrado(gravadoEm = "frequenciaRespiratoriaCifrado")
	private Integer frequenciaRespiratoria;
	@Column(name = "frequencia_respiratoria")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String frequenciaRespiratoriaCifrado;
	@Transient
	@Cifrado(gravadoEm = "temperaturaCifrado")
	private BigDecimal temperatura;
	@Column(name = "temperatura")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String temperaturaCifrado;
	@Transient
	@Cifrado(gravadoEm = "saturacaoOxigenioCifrado")
	private BigDecimal saturacaoOxigenio;
	@Column(name = "saturacao_oxigenio")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String saturacaoOxigenioCifrado;

	public void setPressaoArterial(String pressaoArterial) {
		this.pressaoArterial = pressaoArterial;
		this.pressaoArterialCifrado = Cifrado.PENDENTE;
	}

	public void setFrequenciaCardiaca(Integer frequenciaCardiaca) {
		this.frequenciaCardiaca = frequenciaCardiaca;
		this.frequenciaCardiacaCifrado = Cifrado.PENDENTE;
	}

	public void setFrequenciaRespiratoria(Integer frequenciaRespiratoria) {
		this.frequenciaRespiratoria = frequenciaRespiratoria;
		this.frequenciaRespiratoriaCifrado = Cifrado.PENDENTE;
	}

	public void setTemperatura(BigDecimal temperatura) {
		this.temperatura = temperatura;
		this.temperaturaCifrado = Cifrado.PENDENTE;
	}

	public void setSaturacaoOxigenio(BigDecimal saturacaoOxigenio) {
		this.saturacaoOxigenio = saturacaoOxigenio;
		this.saturacaoOxigenioCifrado = Cifrado.PENDENTE;
	}
}
