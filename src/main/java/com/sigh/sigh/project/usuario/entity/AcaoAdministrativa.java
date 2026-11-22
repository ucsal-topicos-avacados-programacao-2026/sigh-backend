package com.sigh.sigh.project.usuario.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "acao_administrativa")
@Getter
@Setter
public class AcaoAdministrativa {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario ator;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario alvo;

	@Enumerated(EnumType.STRING)
	private TipoDeAcaoAdministrativa acao;

	private String detalhe;

	private LocalDateTime ocorridoEm;
}
