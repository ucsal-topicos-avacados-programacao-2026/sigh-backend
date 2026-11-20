package com.sigh.sigh.project.auditoria.entity;

import com.sigh.sigh.project.comum.cripto.TextoCifrado;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import jakarta.persistence.Convert;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "analise_auditoria")
@Getter
@Setter
public class AnaliseAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private RegistroAuditoria registroAuditoria;

	private LocalDateTime registradaEm;

	@Enumerated(EnumType.STRING)
	private TipoAnalise tipo;

	@Enumerated(EnumType.STRING)
	private ConclusaoAnalise conclusao;

	@Convert(converter = TextoCifrado.class)
	private String descricao;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario auditor;
}
