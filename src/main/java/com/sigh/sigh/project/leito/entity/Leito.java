package com.sigh.sigh.project.leito.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "leito")
@Getter
@Setter
public class Leito {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicao;

	private String codigo;
	private String setor;

	@Enumerated(EnumType.STRING)
	private CategoriaLeito categoria;

	@Enumerated(EnumType.STRING)
	private StatusLeito status;

	public boolean estaDisponivel() {
		return status == StatusLeito.LIVRE;
	}
}
