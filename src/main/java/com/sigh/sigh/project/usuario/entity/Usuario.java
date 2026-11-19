package com.sigh.sigh.project.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String login;
	private String nome;
	private String senhaHash;
	private boolean mfaHabilitado;
	private String mfaSegredo;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "usuario_perfil")
	@Column(name = "perfil")
	@Enumerated(EnumType.STRING)
	private Set<Perfil> perfis = new HashSet<>();
}
