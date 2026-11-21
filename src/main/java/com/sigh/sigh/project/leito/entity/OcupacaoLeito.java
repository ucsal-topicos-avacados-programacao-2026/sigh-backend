package com.sigh.sigh.project.leito.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocupacao_leito")
@Getter
@Setter
public class OcupacaoLeito {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Leito leito;

	@ManyToOne(fetch = FetchType.LAZY)
	private Internacao internacao;

	private LocalDateTime inicioEm;
	private LocalDateTime fimEm;

	@Enumerated(EnumType.STRING)
	private MotivoOcupacao motivoInicio;
}
