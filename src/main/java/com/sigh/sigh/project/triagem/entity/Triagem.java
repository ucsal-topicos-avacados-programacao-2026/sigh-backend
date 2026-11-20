package com.sigh.sigh.project.triagem.entity;

import com.sigh.sigh.project.comum.cripto.TextoCifrado;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import jakarta.persistence.Convert;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "triagem")
@Getter
@Setter
public class Triagem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Atendimento atendimento;

	@Convert(converter = TextoCifrado.class)
	private String sintomas;

	@Embedded
	private SinaisVitais sinaisVitais;

	@Enumerated(EnumType.STRING)
	private CorRisco corSugerida;

	@Enumerated(EnumType.STRING)
	private CorRisco corConfirmada;

	private LocalDateTime confirmadaEm;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario registrador;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario confirmador;

	@ManyToMany
	private Set<RegistroClinico> fontesHistorico = new HashSet<>();
}
