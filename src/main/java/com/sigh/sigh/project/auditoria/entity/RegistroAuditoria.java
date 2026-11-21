package com.sigh.sigh.project.auditoria.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@EntityListeners(CifragemDasEntidades.class)
@Immutable
@Table(name = "registro_auditoria")
@Getter
@Setter
public class RegistroAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario ator;

	@Enumerated(EnumType.STRING)
	private Perfil perfil;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicao;

	private LocalDateTime ocorridoEm;
	private String finalidadeDeclarada;
	private boolean emergencia;
	@Transient
	@Cifrado(gravadoEm = "justificativaCifrado", legadoSe = "justificativaLegada",
			legadoListadoEm = "auditoria_justificativa_legada")
	private String justificativa;
	@Column(name = "justificativa")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String justificativaCifrado;
	@Formula("(exists (select 1 from auditoria_justificativa_legada l where l.registro_id = id))")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private boolean justificativaLegada;

	@ManyToMany
	private Set<RegistroClinico> registrosAcessados = new HashSet<>();

	public void setJustificativa(String justificativa) {
		this.justificativa = justificativa;
		this.justificativaCifrado = Cifrado.PENDENTE;
	}
}
