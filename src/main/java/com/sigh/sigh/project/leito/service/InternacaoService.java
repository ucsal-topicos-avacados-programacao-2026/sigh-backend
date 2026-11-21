package com.sigh.sigh.project.leito.service;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.leito.dto.AtendimentoSemLeitoDTO;
import com.sigh.sigh.project.leito.dto.InternacaoAtivaDTO;
import com.sigh.sigh.project.leito.dto.ReferenciasDaSolicitacao;
import com.sigh.sigh.project.leito.dto.SolicitacaoTransferenciaRequestDTO;
import com.sigh.sigh.project.leito.dto.SolicitacaoTransferenciaResponseDTO;
import com.sigh.sigh.project.leito.entity.SolicitacaoTransferencia;
import com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia;
import com.sigh.sigh.project.leito.repository.SolicitacaoTransferenciaRepository;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.leito.dto.InternacaoRequestDTO;
import com.sigh.sigh.project.leito.dto.InternacaoResponseDTO;
import com.sigh.sigh.project.leito.entity.Internacao;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.MotivoOcupacao;
import com.sigh.sigh.project.leito.entity.OcupacaoLeito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import com.sigh.sigh.project.leito.repository.InternacaoRepository;
import com.sigh.sigh.project.leito.repository.LeitoRepository;
import com.sigh.sigh.project.leito.repository.OcupacaoLeitoRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternacaoService {

	private final InternacaoRepository internacaoRepository;
	private final OcupacaoLeitoRepository ocupacaoLeitoRepository;
	private final LeitoRepository leitoRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final Clock clock;
	private final VinculoService vinculoService;
	private final SolicitacaoTransferenciaRepository solicitacaoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final UsuarioService usuarioService;

	/**
	 * Internações em andamento nos leitos da instituição (FE-04), com o que o administrativo precisa para
	 * transferir ou dar alta. Exige vínculo administrativo ativo com a instituição antes de ler qualquer dado.
	 */
	@Transactional(readOnly = true)
	public List<InternacaoAtivaDTO> internacoesAtivas(UUID instituicaoId, String login) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ADMINISTRATIVO);
		return ocupacaoLeitoRepository.atuaisDaInstituicao(instituicaoId).stream().map(ocupacao -> {
			Internacao internacao = ocupacao.getInternacao();
			Leito leito = ocupacao.getLeito();
			List<InternacaoAtivaDTO.ResumoDeAltaDTO> resumos = registroClinicoRepository
					.resumosDeAltaDesde(internacao.getProntuario().getId(), internacao.getAdmitidaEm()).stream()
					.map(r -> new InternacaoAtivaDTO.ResumoDeAltaDTO(r.getId(), r.getCriadoEm(), r.getAutor().getNome()))
					.toList();
			UUID pendente = solicitacaoRepository.findByInternacaoIdAndStatus(internacao.getId(),
					StatusSolicitacaoTransferencia.PENDENTE).stream().findFirst().map(SolicitacaoTransferencia::getId)
					.orElse(null);
			return new InternacaoAtivaDTO(internacao.getId(), internacao.getAtendimentoOrigem().getId(),
					internacao.getProntuario().getPaciente().getNome(), leito.getId(), leito.getCodigo(), leito.getSetor(),
					leito.getCategoria(), internacao.getAdmitidaEm(), resumos, pendente);
		}).toList();
	}

	/**
	 * Atendimentos em aberto da instituição cujo paciente não está internado (FE-04): de onde se escolhe quem
	 * internar, e a fila de espera quando não há leito livre. Exige vínculo administrativo ativo com a instituição.
	 */
	@Transactional(readOnly = true)
	public List<AtendimentoSemLeitoDTO> atendimentosSemLeito(UUID instituicaoId, String login) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ADMINISTRATIVO);
		return atendimentoRepository.abertosSemInternacao(instituicaoId).stream()
				.map(a -> new AtendimentoSemLeitoDTO(a.getId(), a.getProntuario().getPaciente().getNome(), a.getChegadaEm(),
						a.getStatus(), a.getMotivo()))
				.toList();
	}

	@Transactional
	public InternacaoResponseDTO internar(InternacaoRequestDTO dados, String login) {
		Atendimento atendimento = atendimentoRepository.findById(dados.atendimentoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		vinculoService.exigirVinculoAtivo(login, atendimento.getInstituicao().getId(), Perfil.ADMINISTRATIVO);
		if (internacaoRepository.existsByProntuarioIdAndAltaEmIsNull(atendimento.getProntuario().getId())) {
			throw new RegraNegocioException("O paciente já está internado.");
		}
		Leito leito = buscarLeitoLivre(dados.leitoId());
		if (!leito.getInstituicao().getId().equals(atendimento.getInstituicao().getId())) {
			throw new RegraNegocioException("O leito pertence a outra instituição.");
		}
		Internacao internacao = new Internacao();
		internacao.setProntuario(atendimento.getProntuario());
		internacao.setAtendimentoOrigem(atendimento);
		internacao.setAdmitidaEm(LocalDateTime.now(clock));
		internacaoRepository.save(internacao);
		ocupar(leito, internacao, MotivoOcupacao.INTERNACAO);
		return InternacaoResponseDTO.de(internacao, leito);
	}

	/**
	 * Transferência entre leitos da mesma instituição: registro direto do administrativo dela. Para outra
	 * instituição da rede, é por solicitação, que o destino aceita ou recusa (BE-21).
	 */
	@Transactional
	public InternacaoResponseDTO transferir(UUID internacaoId, UUID leitoDestinoId, String login) {
		Internacao internacao = buscarInternacaoAberta(internacaoId, login);
		Leito destino = buscarLeitoLivre(leitoDestinoId);
		if (!destino.getInstituicao().getId().equals(instituicaoAtual(internacao))) {
			throw new RegraNegocioException("O leito é de outra instituição: a transferência entre instituições é"
					+ " por solicitação, que o destino aceita ou recusa.");
		}
		desocuparLeitoAtual(internacao);
		ocupar(destino, internacao, MotivoOcupacao.TRANSFERENCIA);
		return InternacaoResponseDTO.de(internacao, destino);
	}

	@Transactional
	public InternacaoResponseDTO darAlta(UUID internacaoId, UUID resumoAltaId, String login) {
		Internacao internacao = buscarInternacaoAberta(internacaoId, login);
		RegistroClinico resumoAlta = buscarResumoAlta(resumoAltaId, internacao);
		Leito leito = desocuparLeitoAtual(internacao);
		internacao.setAltaEm(LocalDateTime.now(clock));
		internacao.setResumoAlta(resumoAlta);
		// Com a alta, um pedido de transferência pendente perde o sentido.
		for (SolicitacaoTransferencia pendente : solicitacaoRepository.findByInternacaoIdAndStatus(internacao.getId(),
				StatusSolicitacaoTransferencia.PENDENTE)) {
			decidir(pendente, StatusSolicitacaoTransferencia.CANCELADA, login);
		}
		return InternacaoResponseDTO.de(internacao, leito);
	}

	/**
	 * A origem pede a transferência para outra instituição da rede (Processo 2 da ERS: "solicita-se
	 * transferência para outra unidade da rede"). O paciente continua no leito atual até o destino aceitar.
	 */
	@Transactional
	public SolicitacaoTransferenciaResponseDTO solicitarTransferencia(UUID internacaoId,
			SolicitacaoTransferenciaRequestDTO dados, String login) {
		Internacao internacao = buscarInternacaoAberta(internacaoId, login);
		UUID origem = instituicaoAtual(internacao);
		if (dados.instituicaoDestinoId().equals(origem)) {
			throw new RegraNegocioException("Para outro leito da mesma instituição, use a transferência direta.");
		}
		Instituicao destino = instituicaoRepository.findById(dados.instituicaoDestinoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Instituição de destino não encontrada."));
		if (!destino.isCredenciada()) {
			throw new RegraNegocioException("A instituição de destino não está credenciada na rede.");
		}
		if (!solicitacaoRepository.findByInternacaoIdAndStatus(internacaoId, StatusSolicitacaoTransferencia.PENDENTE)
				.isEmpty()) {
			throw new RegraNegocioException("Já há uma solicitação de transferência pendente para esta internação.");
		}
		SolicitacaoTransferencia solicitacao = new SolicitacaoTransferencia();
		solicitacao.setInternacao(internacao);
		solicitacao.setInstituicaoOrigem(instituicaoRepository.getReferenceById(origem));
		solicitacao.setInstituicaoDestino(destino);
		solicitacao.setCategoria(dados.categoria());
		solicitacao.setStatus(StatusSolicitacaoTransferencia.PENDENTE);
		solicitacao.setSolicitadaEm(LocalDateTime.now(clock));
		solicitacao.setSolicitante(usuarioService.buscarPorLogin(login));
		return SolicitacaoTransferenciaResponseDTO.de(solicitacaoRepository.saveAndFlush(solicitacao));
	}

	/** Pendentes para o destino, do mais antigo para o mais novo. Só o administrativo do destino as vê. */
	@Transactional(readOnly = true)
	public Page<SolicitacaoTransferenciaResponseDTO> solicitacoesRecebidas(UUID instituicaoId, String login,
			Pageable paginacao) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ADMINISTRATIVO);
		return solicitacaoRepository.buscarPendentesParaODestino(instituicaoId, paginacao)
				.map(SolicitacaoTransferenciaResponseDTO::de);
	}

	/** Pedidos feitos pela origem, com o status e o motivo da recusa. Só o administrativo da origem os vê. */
	@Transactional(readOnly = true)
	public Page<SolicitacaoTransferenciaResponseDTO> solicitacoesEnviadas(UUID instituicaoId, String login,
			Pageable paginacao) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ADMINISTRATIVO);
		return solicitacaoRepository.buscarEnviadasPelaOrigem(instituicaoId, paginacao)
				.map(SolicitacaoTransferenciaResponseDTO::de);
	}

	/**
	 * O destino aceita escolhendo um leito livre dele, da categoria pedida ("a unidade de destino executa
	 * este mesmo processo"). O paciente deixa o leito da origem, que vai para higienização.
	 */
	@Transactional
	public SolicitacaoTransferenciaResponseDTO aceitarTransferencia(UUID solicitacaoId, UUID leitoId, String login) {
		SolicitacaoTransferencia solicitacao = travarPendente(solicitacaoId, login, true);
		if (!solicitacao.getInstituicaoDestino().isCredenciada()) {
			throw new RegraNegocioException("A instituição de destino não está mais credenciada na rede.");
		}
		Internacao internacao = solicitacao.getInternacao();
		if (internacao.getAltaEm() != null
				|| !solicitacao.getInstituicaoOrigem().getId().equals(instituicaoAtual(internacao))) {
			throw new RegraNegocioException("O paciente não está mais internado na instituição que pediu a transferência.");
		}
		Leito leito = buscarLeitoLivre(leitoId);
		if (!leito.getInstituicao().getId().equals(solicitacao.getInstituicaoDestino().getId())) {
			throw new RegraNegocioException("O leito precisa ser da instituição de destino.");
		}
		if (leito.getCategoria() != solicitacao.getCategoria()) {
			throw new RegraNegocioException("O leito precisa ser da categoria pedida (" + solicitacao.getCategoria() + ").");
		}
		desocuparLeitoAtual(internacao);
		ocupar(leito, internacao, MotivoOcupacao.TRANSFERENCIA);
		solicitacao.setLeitoDestino(leito);
		decidir(solicitacao, StatusSolicitacaoTransferencia.ACEITA, login);
		return SolicitacaoTransferenciaResponseDTO.de(solicitacao);
	}

	@Transactional
	public SolicitacaoTransferenciaResponseDTO recusarTransferencia(UUID solicitacaoId, String motivo, String login) {
		SolicitacaoTransferencia solicitacao = travarPendente(solicitacaoId, login, true);
		solicitacao.setMotivoRecusa(motivo.strip());
		decidir(solicitacao, StatusSolicitacaoTransferencia.RECUSADA, login);
		return SolicitacaoTransferenciaResponseDTO.de(solicitacao);
	}

	/** Só a origem cancela, e só enquanto estiver pendente. */
	@Transactional
	public SolicitacaoTransferenciaResponseDTO cancelarTransferencia(UUID solicitacaoId, String login) {
		SolicitacaoTransferencia solicitacao = travarPendente(solicitacaoId, login, false);
		decidir(solicitacao, StatusSolicitacaoTransferencia.CANCELADA, login);
		return SolicitacaoTransferenciaResponseDTO.de(solicitacao);
	}

	/**
	 * Confere o vínculo (com o destino ou com a origem) antes de travar qualquer coisa; depois trava a
	 * internação e a solicitação, nessa ordem (a mesma da alta e da transferência direta), e confere que
	 * ainda está pendente.
	 */
	private SolicitacaoTransferencia travarPendente(UUID solicitacaoId, String login, boolean peloDestino) {
		ReferenciasDaSolicitacao referencias = solicitacaoRepository.buscarReferencias(solicitacaoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação de transferência não encontrada."));
		vinculoService.exigirVinculoAtivo(login,
				peloDestino ? referencias.instituicaoDestinoId() : referencias.instituicaoOrigemId(), Perfil.ADMINISTRATIVO);
		internacaoRepository.findComBloqueioById(referencias.internacaoId());
		SolicitacaoTransferencia solicitacao = solicitacaoRepository.findComBloqueioById(solicitacaoId).orElseThrow();
		exigirPendente(solicitacao);
		return solicitacao;
	}

	private static void exigirPendente(SolicitacaoTransferencia solicitacao) {
		if (solicitacao.getStatus() != StatusSolicitacaoTransferencia.PENDENTE) {
			throw new RegraNegocioException("A solicitação de transferência já foi decidida.");
		}
	}

	private void decidir(SolicitacaoTransferencia solicitacao, StatusSolicitacaoTransferencia status, String login) {
		solicitacao.setStatus(status);
		solicitacao.setDecididaEm(LocalDateTime.now(clock));
		solicitacao.setDecisor(usuarioService.buscarPorLogin(login));
	}

	/** Transferência e alta exigem vínculo com a instituição onde o paciente está internado agora. */
	private Internacao buscarInternacaoAberta(UUID internacaoId, String login) {
		Internacao internacao = internacaoRepository.findComBloqueioById(internacaoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Internação não encontrada."));
		vinculoService.exigirVinculoAtivo(login, instituicaoAtual(internacao), Perfil.ADMINISTRATIVO);
		if (internacao.getAltaEm() != null) {
			throw new RegraNegocioException("A internação já foi encerrada.");
		}
		return internacao;
	}

	/** Instituição onde o paciente está internado agora: a do leito ocupado (ou a do atendimento de origem). */
	private UUID instituicaoAtual(Internacao internacao) {
		return ocupacaoLeitoRepository.instituicaoDaOcupacaoAtual(internacao.getId())
				.orElseGet(() -> internacao.getAtendimentoOrigem().getInstituicao().getId());
	}

	private Leito buscarLeitoLivre(UUID leitoId) {
		Leito leito = leitoRepository.findComBloqueioById(leitoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Leito não encontrado."));
		if (!leito.estaDisponivel()) {
			throw new RegraNegocioException("O leito não está livre.");
		}
		return leito;
	}

	private RegistroClinico buscarResumoAlta(UUID resumoAltaId, Internacao internacao) {
		RegistroClinico resumo = registroClinicoRepository.findById(resumoAltaId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Resumo de alta não encontrado."));
		if (resumo.getTipo() != TipoRegistro.RESUMO_ALTA) {
			throw new RegraNegocioException("O registro informado não é um resumo de alta.");
		}
		if (!resumo.getProntuario().getId().equals(internacao.getProntuario().getId())) {
			throw new RegraNegocioException("O resumo de alta pertence a outro prontuário.");
		}
		return resumo;
	}

	private void ocupar(Leito leito, Internacao internacao, MotivoOcupacao motivo) {
		OcupacaoLeito ocupacao = new OcupacaoLeito();
		ocupacao.setLeito(leito);
		ocupacao.setInternacao(internacao);
		ocupacao.setInicioEm(LocalDateTime.now(clock));
		ocupacao.setMotivoInicio(motivo);
		ocupacaoLeitoRepository.save(ocupacao);
		leito.setStatus(StatusLeito.OCUPADO);
	}

	private Leito desocuparLeitoAtual(Internacao internacao) {
		OcupacaoLeito ocupacao = ocupacaoLeitoRepository.buscarOcupacaoAtual(internacao.getId())
				.orElseThrow(() -> new RegraNegocioException("A internação não tem leito ocupado."));
		ocupacao.setFimEm(LocalDateTime.now(clock));
		Leito leito = ocupacao.getLeito();
		leito.setStatus(StatusLeito.EM_HIGIENIZACAO);
		return leito;
	}
}
