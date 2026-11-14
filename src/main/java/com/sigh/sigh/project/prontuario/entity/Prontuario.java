package com.sigh.sigh.project.prontuario.entity;

import com.sigh.sigh.project.paciente.entity.Paciente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prontuario")
@Getter
@Setter
public class Prontuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY)
	private Paciente paciente;

	private LocalDateTime criadoEm;
}
