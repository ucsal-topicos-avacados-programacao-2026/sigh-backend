package com.sigh.sigh.project.auditoria.entity;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import lombok.AccessLevel;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(CifragemDasEntidades.class)
@Table(name = "analise_auditoria")
@Getter
@Setter
public class AnaliseAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private RegistroAuditoria registroAuditoria;

	private LocalDateTime registradaEm;

	@Enumerated(EnumType.STRING)
	private TipoAnalise tipo;

	@Enumerated(EnumType.STRING)
	private ConclusaoAnalise conclusao;

	@Transient
	@Cifrado(gravadoEm = "descricaoCifrado")
	private String descricao;
	@Column(name = "descricao")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String descricaoCifrado;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario auditor;

	public void setDescricao(String descricao) {
		this.descricao = descricao;
		this.descricaoCifrado = Cifrado.PENDENTE;
	}
}
