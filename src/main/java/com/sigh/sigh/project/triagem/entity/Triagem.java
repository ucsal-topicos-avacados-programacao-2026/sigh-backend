package com.sigh.sigh.project.triagem.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@EntityListeners(CifragemDasEntidades.class)
@Table(name = "triagem")
@Getter
@Setter
public class Triagem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Atendimento atendimento;

	@Transient
	@Cifrado(gravadoEm = "sintomasCifrado")
	private String sintomas;
	@Column(name = "sintomas")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String sintomasCifrado;

	@Embedded
	private SinaisVitais sinaisVitais;

	@Enumerated(EnumType.STRING)
	private CorRisco corSugerida;

	@Enumerated(EnumType.STRING)
	private CorRisco corConfirmada;

	private LocalDateTime confirmadaEm;

	private boolean sugestaoPreliminar;

	@Enumerated(EnumType.STRING)
	private SituacaoSugestao situacaoSugestao;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario registrador;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario confirmador;

	@ManyToMany
	private Set<RegistroClinico> fontesHistorico = new HashSet<>();

	public void setSintomas(String sintomas) {
		this.sintomas = sintomas;
		this.sintomasCifrado = Cifrado.PENDENTE;
	}
}
