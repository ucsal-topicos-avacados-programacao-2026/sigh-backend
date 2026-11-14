package com.sigh.sigh.project.auditoria.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "registro_auditoria")
@Getter
@Setter
public class RegistroAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Prontuario prontuario;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario ator;

	@Enumerated(EnumType.STRING)
	private Perfil perfil;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicao;

	private LocalDateTime ocorridoEm;
	private String finalidadeDeclarada;
	private boolean emergencia;
	private String justificativa;

	@ManyToMany
	private Set<RegistroClinico> registrosAcessados = new HashSet<>();
}
