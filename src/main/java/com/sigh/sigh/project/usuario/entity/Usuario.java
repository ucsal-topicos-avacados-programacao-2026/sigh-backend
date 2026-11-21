package com.sigh.sigh.project.usuario.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@EntityListeners(CifragemDasEntidades.class)
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
	@Transient
	@Cifrado(gravadoEm = "mfaSegredoCifrado")
	private String mfaSegredo;
	@Column(name = "mfa_segredo")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String mfaSegredoCifrado;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "usuario_perfil")
	@Column(name = "perfil")
	@Enumerated(EnumType.STRING)
	private Set<Perfil> perfis = new HashSet<>();

	public void setMfaSegredo(String mfaSegredo) {
		this.mfaSegredo = mfaSegredo;
		this.mfaSegredoCifrado = Cifrado.PENDENTE;
	}
}
