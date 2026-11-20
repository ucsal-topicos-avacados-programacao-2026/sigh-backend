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
@Table(name = "registro_clinico")
@Getter
@Setter
public class RegistroClinico {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Atendimento atendimento;

	@Enumerated(EnumType.STRING)
	private TipoRegistro tipo;

	private LocalDateTime criadoEm;
	@Transient
	@Cifrado(gravadoEm = "conteudoCifrado")
	private String conteudo;
	@Column(name = "conteudo")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String conteudoCifrado;
	private String categoriaInformacao;

	@Transient
	@Cifrado(gravadoEm = "codigoCID10Cifrado")
	private String codigoCID10;
	@Column(name = "codigo_cid10")
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private String codigoCID10Cifrado;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario autor;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao origem;

	public void setConteudo(String conteudo) {
		this.conteudo = conteudo;
		this.conteudoCifrado = Cifrado.PENDENTE;
	}

	public void setCodigoCID10(String codigoCID10) {
		this.codigoCID10 = codigoCID10;
		this.codigoCID10Cifrado = Cifrado.PENDENTE;
	}
}
