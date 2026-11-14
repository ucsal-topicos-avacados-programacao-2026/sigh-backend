package com.sigh.sigh.project.prontuario.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
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
	private String conteudo;
	private String categoriaInformacao;

	@Column(name = "codigo_cid10")
	private String codigoCID10;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario autor;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao origem;
}
