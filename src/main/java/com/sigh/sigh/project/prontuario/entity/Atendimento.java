package com.sigh.sigh.project.prontuario.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(CifragemDasEntidades.class)
@Table(name = "atendimento")
@Getter
@Setter
public class Atendimento {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicao;

	private LocalDateTime chegadaEm;
	private LocalDateTime inicioClinicoEm;
	@Transient
	@Cifrado(gravadoEm = "motivoCifrado")
	private String motivo;
	@Column(name = "motivo")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String motivoCifrado;

	@Enumerated(EnumType.STRING)
	private StatusAtendimento status;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario medicoResponsavel;

	public void setMotivo(String motivo) {
		this.motivo = motivo;
		this.motivoCifrado = Cifrado.PENDENTE;
	}
}
