package com.sigh.sigh.project.leito.entity;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "internacao")
@Getter
@Setter
public class Internacao {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Atendimento atendimentoOrigem;

	private LocalDateTime admitidaEm;
	private LocalDateTime altaEm;

	@ManyToOne(fetch = FetchType.LAZY)
	private RegistroClinico resumoAlta;
}
