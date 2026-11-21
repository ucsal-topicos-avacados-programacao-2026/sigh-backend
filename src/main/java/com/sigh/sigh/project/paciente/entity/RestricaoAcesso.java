package com.sigh.sigh.project.paciente.entity;

import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restricao_acesso")
@Getter
@Setter
public class RestricaoAcesso {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	private LocalDateTime inicioEm;
	private LocalDateTime fimEm;
	private String categoriaAlvo;

	@ManyToOne(fetch = FetchType.LAZY)
	private RegistroClinico registroAlvo;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario definidaPor;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario encerradaPor;
}
