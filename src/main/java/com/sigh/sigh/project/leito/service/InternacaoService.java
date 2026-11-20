package com.sigh.sigh.project.leito.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternacaoService {

	private final InternacaoRepository internacaoRepository;
	private final OcupacaoLeitoRepository ocupacaoLeitoRepository;
	private final LeitoRepository leitoRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;

	@Transactional
	public InternacaoResponseDTO internar(InternacaoRequestDTO dados) {
		Atendimento atendimento = atendimentoRepository.findById(dados.atendimentoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		if (internacaoRepository.existsByProntuarioIdAndAltaEmIsNull(atendimento.getProntuario().getId())) {
			throw new RegraNegocioException("O paciente já está internado.");
		}
		Leito leito = buscarLeitoLivre(dados.leitoId());
		Internacao internacao = new Internacao();
		internacao.setProntuario(atendimento.getProntuario());
		internacao.setAtendimentoOrigem(atendimento);
		internacao.setAdmitidaEm(LocalDateTime.now());
		internacaoRepository.save(internacao);
		ocupar(leito, internacao, MotivoOcupacao.INTERNACAO);
		return InternacaoResponseDTO.de(internacao, leito);
	}

	@Transactional
	public InternacaoResponseDTO transferir(UUID internacaoId, UUID leitoDestinoId) {
		Internacao internacao = buscarInternacaoAberta(internacaoId);
		Leito destino = buscarLeitoLivre(leitoDestinoId);
		desocuparLeitoAtual(internacao);
		ocupar(destino, internacao, MotivoOcupacao.TRANSFERENCIA);
		return InternacaoResponseDTO.de(internacao, destino);
	}

	@Transactional
	public InternacaoResponseDTO darAlta(UUID internacaoId, UUID resumoAltaId) {
		Internacao internacao = buscarInternacaoAberta(internacaoId);
		RegistroClinico resumoAlta = buscarResumoAlta(resumoAltaId, internacao);
		Leito leito = desocuparLeitoAtual(internacao);
		internacao.setAltaEm(LocalDateTime.now());
		internacao.setResumoAlta(resumoAlta);
		return InternacaoResponseDTO.de(internacao, leito);
	}

	private Internacao buscarInternacaoAberta(UUID internacaoId) {
		Internacao internacao = internacaoRepository.findById(internacaoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Internação não encontrada."));
		if (internacao.getAltaEm() != null) {
			throw new RegraNegocioException("A internação já foi encerrada.");
		}
		return internacao;
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
		ocupacao.setInicioEm(LocalDateTime.now());
		ocupacao.setMotivoInicio(motivo);
		ocupacaoLeitoRepository.save(ocupacao);
		leito.setStatus(StatusLeito.OCUPADO);
	}

	private Leito desocuparLeitoAtual(Internacao internacao) {
		OcupacaoLeito ocupacao = ocupacaoLeitoRepository.buscarOcupacaoAtual(internacao.getId())
				.orElseThrow(() -> new RegraNegocioException("A internação não tem leito ocupado."));
		ocupacao.setFimEm(LocalDateTime.now());
		Leito leito = ocupacao.getLeito();
		leito.setStatus(StatusLeito.EM_HIGIENIZACAO);
		return leito;
	}
}
