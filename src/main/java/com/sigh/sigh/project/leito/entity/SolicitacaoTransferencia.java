package com.sigh.sigh.project.leito.entity;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido de transferência de uma internação para outra instituição da rede (RF-006, BE-21). Quem decide
 * é o administrativo do destino: aceita escolhendo um leito livre dele, ou recusa com motivo.
 */
@Entity
@Table(name = "solicitacao_transferencia")
@Getter
@Setter
public class SolicitacaoTransferencia {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Internacao internacao;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicaoOrigem;

	@ManyToOne(fetch = FetchType.LAZY)
	private Instituicao instituicaoDestino;

	@Enumerated(EnumType.STRING)
	private CategoriaLeito categoria;

	@Enumerated(EnumType.STRING)
	private StatusSolicitacaoTransferencia status;

	private LocalDateTime solicitadaEm;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario solicitante;

	private LocalDateTime decididaEm;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario decisor;

	@ManyToOne(fetch = FetchType.LAZY)
	private Leito leitoDestino;

	private String motivoRecusa;
}
