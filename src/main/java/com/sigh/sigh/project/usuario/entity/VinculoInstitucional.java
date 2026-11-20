package com.sigh.sigh.project.usuario.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "vinculo_institucional")
@Getter
@Setter
public class VinculoInstitucional {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicao;

	@ElementCollection
	@CollectionTable(name = "vinculo_perfil")
	@Column(name = "perfil")
	@Enumerated(EnumType.STRING)
	private Set<Perfil> perfisHabilitados = new HashSet<>();

	private String registroProfissional;
	private LocalDateTime inicioEm;
	private LocalDateTime fimEm;

	public boolean estaAtivo(LocalDateTime agora) {
		return fimEm == null || fimEm.isAfter(agora);
	}
}
