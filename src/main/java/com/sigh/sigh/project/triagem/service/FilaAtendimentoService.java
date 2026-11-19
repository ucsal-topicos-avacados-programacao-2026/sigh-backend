package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.triagem.config.TriagemProperties;
import com.sigh.sigh.project.triagem.dto.PacienteNaFilaDTO;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilaAtendimentoService {

	private final TriagemRepository triagemRepository;
	private final TriagemProperties triagemProperties;

	@Transactional(readOnly = true)
	public Page<PacienteNaFilaDTO> consultarFila(UUID instituicaoId, Pageable paginacao) {
		LocalDateTime agora = LocalDateTime.now();
		return triagemRepository.buscarFila(instituicaoId, paginacao)
				.map(triagem -> paraPacienteNaFila(triagem, agora));
	}

	private PacienteNaFilaDTO paraPacienteNaFila(Triagem triagem, LocalDateTime agora) {
		Atendimento atendimento = triagem.getAtendimento();
		Duration espera = Duration.between(atendimento.getChegadaEm(), agora);
		Duration tempoMaximo = triagemProperties.tempoMaximoDe(triagem.getCorConfirmada());
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
