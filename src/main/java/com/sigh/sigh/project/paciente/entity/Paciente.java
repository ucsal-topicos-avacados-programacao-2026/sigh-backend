package com.sigh.sigh.project.paciente.entity;

import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "paciente")
@Getter
@Setter
public class Paciente {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String cpf;
	private String nome;
	private LocalDate dataNascimento;

	@OneToOne(fetch = FetchType.LAZY)
	private Usuario conta;
}
