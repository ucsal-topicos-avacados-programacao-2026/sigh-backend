package com.sigh.sigh.project.paciente.entity;

import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "representacao_legal")
@Getter
@Setter
public class RepresentacaoLegal {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Paciente paciente;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario representante;

	private LocalDateTime inicioEm;
	private LocalDateTime fimEm;
	private String documento;

	public boolean validarVigencia() {
		LocalDateTime agora = LocalDateTime.now();
		return !inicioEm.isAfter(agora) && (fimEm == null || fimEm.isAfter(agora));
	}
}
