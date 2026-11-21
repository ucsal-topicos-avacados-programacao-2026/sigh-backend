package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.triagem.dto.PreClassificacaoResponseDTO;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SituacaoSugestao;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TriagemService {

	/**
	 * A triagem de chegada (aberto ou em triagem) e a reclassificação durante o atendimento (BE-24): o
	 * paciente pode piorar ou melhorar depois de o médico começar. Só o atendimento encerrado não aceita.
	 */
	private static final Set<StatusAtendimento> STATUS_QUE_ACEITAM_TRIAGEM =
			EnumSet.of(StatusAtendimento.ABERTO, StatusAtendimento.EM_TRIAGEM, StatusAtendimento.EM_ATENDIMENTO);

	private final TriagemRepository triagemRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;
	private final VinculoService vinculoService;
	private final ApplicationEventPublisher eventos;

	@Transactional
	public TriagemResponseDTO registrar(TriagemRequestDTO dados, String loginEnfermeiro) {
		Atendimento atendimento = atendimentoRepository.findById(dados.atendimentoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		vinculoService.exigirVinculoAtivo(loginEnfermeiro, atendimento.getInstituicao().getId(), Perfil.ENFERMEIRO);
		if (!STATUS_QUE_ACEITAM_TRIAGEM.contains(atendimento.getStatus())) {
			throw new RegraNegocioException("Não é possível classificar o risco de um atendimento encerrado.");
		}
		Triagem triagem = new Triagem();
		triagem.setAtendimento(atendimento);
		triagem.setSintomas(dados.sintomas());
		triagem.setSinaisVitais(dados.sinaisVitais().paraEntidade());
		triagem.setRegistrador(usuarioService.buscarPorLogin(loginEnfermeiro));
		// A reclassificação durante o atendimento não devolve o paciente para a fila de triagem.
		if (atendimento.getStatus() != StatusAtendimento.EM_ATENDIMENTO) {
			atendimento.setStatus(StatusAtendimento.EM_TRIAGEM);
		}
		return TriagemResponseDTO.de(triagemRepository.save(triagem));
	}

	/**
	 * A triagem nasce na admissão, com o motivo dito na recepção como sintoma e sem sinais vitais, e a IA é
	 * chamada em segundo plano depois que a admissão for gravada. A recepção só informa o que o paciente sente.
	 */
	@Transactional
	public void abrirNaAdmissao(Atendimento atendimento, String loginRecepcao) {
		Triagem triagem = new Triagem();
		triagem.setAtendimento(atendimento);
		triagem.setSintomas(atendimento.getMotivo());
		triagem.setRegistrador(usuarioService.buscarPorLogin(loginRecepcao));
		triagem.setSugestaoPreliminar(true);
		triagem.setSituacaoSugestao(SituacaoSugestao.EM_ANDAMENTO);
		triagemRepository.save(triagem);
		eventos.publishEvent(new SugestaoSolicitada(triagem.getId(), loginRecepcao, Perfil.RECEPCAO,
				"Pré-classificação por IA na admissão"));
	}

	/** O enfermeiro mede os sinais vitais numa triagem que já nasceu na recepção; a IA refaz a sugestão sozinha. */
	@Transactional
	public TriagemResponseDTO registrarSinaisVitais(UUID triagemId, SinaisVitaisDTO sinaisVitais, String loginEnfermeiro) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		vinculoService.exigirVinculoAtivo(loginEnfermeiro, triagem.getAtendimento().getInstituicao().getId(),
				Perfil.ENFERMEIRO);
		triagem = buscarTriagemNaoConfirmada(triagemId);
		triagem.setSinaisVitais(sinaisVitais.paraEntidade());
		triagem.setSituacaoSugestao(SituacaoSugestao.EM_ANDAMENTO);
		if (triagem.getAtendimento().getStatus() == StatusAtendimento.ABERTO) {
			triagem.getAtendimento().setStatus(StatusAtendimento.EM_TRIAGEM);
		}
		eventos.publishEvent(new SugestaoSolicitada(triagem.getId(), loginEnfermeiro, Perfil.ENFERMEIRO,
				"Triagem com apoio de IA"));
		return TriagemResponseDTO.de(triagem);
	}

	/** A IA falhou ou demorou: a triagem segue sem sugestão nova, e a cor fica com o enfermeiro. */
	@Transactional
	public void marcarSugestaoIndisponivel(UUID triagemId) {
		triagemRepository.findById(triagemId).filter(t -> t.getCorConfirmada() == null)
				.ifPresent(t -> t.setSituacaoSugestao(SituacaoSugestao.INDISPONIVEL));
	}

	/** Andamento da sugestão, para a recepção acompanhar sem ver a cor (RF-012). */
	@Transactional(readOnly = true)
	public PreClassificacaoResponseDTO andamentoDaSugestao(UUID atendimentoId, UUID instituicaoId, String loginRecepcao) {
		vinculoService.exigirVinculoAtivo(loginRecepcao, instituicaoId, Perfil.RECEPCAO);
		Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
				.filter(a -> a.getInstituicao().getId().equals(instituicaoId))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		SituacaoSugestao situacao = triagemRepository.findByAtendimentoIdAndCorConfirmadaIsNull(atendimento.getId())
				.stream().map(Triagem::getSituacaoSugestao).filter(s -> s != null)
				.min(Comparator.naturalOrder()).orElse(null);
		return new PreClassificacaoResponseDTO(situacao);
	}

	@Transactional
	public TriagemResponseDTO registrarSugestao(UUID triagemId, CorRisco corSugerida, Set<UUID> registrosUsados) {
		Triagem triagem = buscarTriagemNaoConfirmada(triagemId);
		triagem.setCorSugerida(corSugerida);
		triagem.setFontesHistorico(referenciasDosRegistros(registrosUsados));
		triagem.setSugestaoPreliminar(triagem.getSinaisVitais() == null);
		triagem.setSituacaoSugestao(SituacaoSugestao.PRONTA);
		return TriagemResponseDTO.de(triagem);
	}

	@Transactional
	public TriagemResponseDTO confirmarRisco(UUID triagemId, CorRisco cor, String loginEnfermeiro) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		vinculoService.exigirVinculoAtivo(loginEnfermeiro, triagem.getAtendimento().getInstituicao().getId(),
				Perfil.ENFERMEIRO);
		triagem = buscarTriagemNaoConfirmada(triagemId);
		triagem.setCorConfirmada(cor);
		triagem.setConfirmadaEm(LocalDateTime.now(clock));
		triagem.setConfirmador(usuarioService.buscarPorLogin(loginEnfermeiro));
		return TriagemResponseDTO.de(triagem);
	}

	private Triagem buscarTriagemNaoConfirmada(UUID triagemId) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		if (triagem.getCorConfirmada() != null) {
			throw new RegraNegocioException("A classificação desta triagem já foi confirmada.");
		}
		if (triagem.getAtendimento().getStatus() == StatusAtendimento.ENCERRADO) {
			throw new RegraNegocioException("Não é possível classificar o risco de um atendimento encerrado.");
		}
		return triagem;
	}

	private Set<RegistroClinico> referenciasDosRegistros(Set<UUID> ids) {
		return ids.stream()
				.map(registroClinicoRepository::getReferenceById)
				.collect(Collectors.toSet());
	}
}
