package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.triagem.config.TempoMaximoEsperaProperties;
import com.sigh.sigh.project.triagem.dto.AguardandoTriagemDTO;
import com.sigh.sigh.project.triagem.dto.PacienteNaFilaDTO;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilaAtendimentoService {

	private final TriagemRepository triagemRepository;
	private final TempoMaximoEsperaProperties tempoMaximoEspera;
	private final Clock clock;
	private final VinculoService vinculoService;

	@Transactional(readOnly = true)
	public Page<PacienteNaFilaDTO> consultarFila(UUID instituicaoId, String login, Pageable paginacao) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ENFERMEIRO, Perfil.MEDICO);
		LocalDateTime agora = LocalDateTime.now(clock);
		return triagemRepository.buscarFila(instituicaoId, paginacao)
				.map(triagem -> paraPacienteNaFila(triagem, agora));
	}

	@Transactional(readOnly = true)
	public List<AguardandoTriagemDTO> aguardandoTriagem(UUID instituicaoId, String login) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.ENFERMEIRO);
		return triagemRepository.aguardandoTriagem(instituicaoId).stream().map(atendimento -> {
			AguardandoTriagemDTO.TriagemPendente pendente = triagemRepository
					.findByAtendimentoIdAndCorConfirmadaIsNull(atendimento.getId()).stream()
					.min(Comparator.comparing((Triagem t) -> t.getCorSugerida() == null).thenComparing(Triagem::getId))
					.map(t -> new AguardandoTriagemDTO.TriagemPendente(t.getId(), t.getSintomas(),
							SinaisVitaisDTO.de(t.getSinaisVitais()), t.getCorSugerida(), t.isSugestaoPreliminar(),
							t.getSituacaoSugestao()))
					.orElse(null);
			return new AguardandoTriagemDTO(atendimento.getId(), atendimento.getProntuario().getPaciente().getNome(),
					atendimento.getChegadaEm(), atendimento.getMotivo(), pendente);
		}).toList();
	}

	private PacienteNaFilaDTO paraPacienteNaFila(Triagem triagem, LocalDateTime agora) {
		Atendimento atendimento = triagem.getAtendimento();
		Duration espera = Duration.between(atendimento.getChegadaEm(), agora);
		Duration tempoMaximo = tempoMaximoEspera.da(triagem.getCorConfirmada());
		return new PacienteNaFilaDTO(
				triagem.getId(),
				atendimento.getId(),
				atendimento.getProntuario().getPaciente().getNome(),
				triagem.getCorConfirmada(),
				atendimento.getChegadaEm(),
				espera.toMinutes(),
				espera.compareTo(tempoMaximo) > 0);
	}
}
