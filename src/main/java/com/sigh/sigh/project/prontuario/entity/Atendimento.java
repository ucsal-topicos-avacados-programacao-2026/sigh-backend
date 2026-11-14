package com.sigh.sigh.project.prontuario.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
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
	private String motivo;

	@Enumerated(EnumType.STRING)
	private StatusAtendimento status;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario medicoResponsavel;
}
