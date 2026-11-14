package com.sigh.sigh.project.ia.entity;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "resumo_historico_ia")
@Getter
@Setter
public class ResumoHistoricoIA {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Atendimento atendimento;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario solicitante;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario validador;

	@Enumerated(EnumType.STRING)
	private StatusResumo status;

	private String texto;
	private LocalDateTime geradoEm;
	private String modeloVersao;

	@ManyToMany
	@JoinTable(name = "resumo_historico_ia_fontes", joinColumns = @JoinColumn(name = "resumo_historico_ia_id"))
	private Set<RegistroClinico> fontes = new HashSet<>();
}
