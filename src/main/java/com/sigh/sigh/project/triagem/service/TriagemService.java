package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TriagemService {

	private final TriagemRepository triagemRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final UsuarioService usuarioService;

	@Transactional
	public TriagemResponseDTO registrar(TriagemRequestDTO dados, String loginEnfermeiro) {
		Atendimento atendimento = atendimentoRepository.findById(dados.atendimentoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		if (atendimento.getStatus() == StatusAtendimento.ENCERRADO) {
			throw new RegraNegocioException("O atendimento já foi encerrado.");
		}
		Triagem triagem = new Triagem();
		triagem.setAtendimento(atendimento);
		triagem.setSintomas(dados.sintomas());
		triagem.setSinaisVitais(dados.sinaisVitais().paraEntidade());
		triagem.setRegistrador(usuarioService.buscarPorLogin(loginEnfermeiro));
		atendimento.setStatus(StatusAtendimento.EM_TRIAGEM);
		return TriagemResponseDTO.de(triagemRepository.save(triagem));
	}

	@Transactional
	public TriagemResponseDTO registrarSugestao(UUID triagemId, CorRisco corSugerida, Set<UUID> registrosUsados) {
		Triagem triagem = buscarTriagemNaoConfirmada(triagemId);
		triagem.setCorSugerida(corSugerida);
		triagem.setFontesHistorico(referenciasDosRegistros(registrosUsados));
		return TriagemResponseDTO.de(triagem);
	}

	@Transactional
	public TriagemResponseDTO confirmarRisco(UUID triagemId, CorRisco cor, String loginEnfermeiro) {
		Triagem triagem = buscarTriagemNaoConfirmada(triagemId);
		triagem.setCorConfirmada(cor);
		triagem.setConfirmadaEm(LocalDateTime.now());
		triagem.setConfirmador(usuarioService.buscarPorLogin(loginEnfermeiro));
		return TriagemResponseDTO.de(triagem);
	}

	private Triagem buscarTriagemNaoConfirmada(UUID triagemId) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		if (triagem.getCorConfirmada() != null) {
			throw new RegraNegocioException("A classificação desta triagem já foi confirmada.");
		}
		return triagem;
	}

	private Set<RegistroClinico> referenciasDosRegistros(Set<UUID> ids) {
		return ids.stream()
				.map(registroClinicoRepository::getReferenceById)
				.collect(Collectors.toSet());
	}
}
