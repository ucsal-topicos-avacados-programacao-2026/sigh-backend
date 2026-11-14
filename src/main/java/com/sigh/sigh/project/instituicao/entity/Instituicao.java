package com.sigh.sigh.project.instituicao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "instituicao")
@Getter
@Setter
public class Instituicao {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String nome;
	private boolean credenciada;

	@Enumerated(EnumType.STRING)
	private TipoInstituicao tipo;
}
