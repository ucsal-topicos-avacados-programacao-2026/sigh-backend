package com.sigh.sigh.project.paciente.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consentimento")
@Getter
@Setter
public class Consentimento {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Paciente paciente;

	private Integer versao;

	@Enumerated(EnumType.STRING)
	private TipoConsentimento tipo;

	@Enumerated(EnumType.STRING)
	private DecisaoConsentimento decisao;

	private LocalDateTime vigenteDesde;
	private String categoriaAlvo;

	@ManyToOne(fetch = FetchType.LAZY)
	private RegistroClinico registroAlvo;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao destinataria;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario outorgante;

	@ManyToOne(fetch = FetchType.LAZY)
	private Consentimento anterior;
}
