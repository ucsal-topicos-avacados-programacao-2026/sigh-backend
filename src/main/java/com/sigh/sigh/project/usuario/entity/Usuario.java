package com.sigh.sigh.project.usuario.entity;

import com.sigh.sigh.project.comum.cripto.TextoCifrado;
import jakarta.persistence.*;
import jakarta.persistence.Convert;
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
	/** Semente do TOTP: quem a tem gera os códigos do segundo fator, então fica cifrada (RNF-004, RNF-005). */
	@Convert(converter = TextoCifrado.class)
	private String mfaSegredo;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "usuario_perfil")
	@Column(name = "perfil")
	@Enumerated(EnumType.STRING)
	private Set<Perfil> perfis = new HashSet<>();
}
