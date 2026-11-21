package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.paciente.dto.PacienteGerenciadoResponse;
import com.sigh.sigh.project.paciente.dto.PacienteGerenciadoResponse.Relacao;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.paciente.repository.RepresentacaoLegalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TitularidadeService {

	static final String SEM_PERMISSAO = "Sem permissão para gerenciar os dados deste paciente.";

	private final PacienteRepository pacienteRepository;
	private final RepresentacaoLegalRepository representacaoRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Paciente pacienteGerenciado(String login, UUID pacienteId) {
		Paciente paciente = pacienteRepository.findById(pacienteId)
				.orElseThrow(() -> new AccessDeniedException(SEM_PERMISSAO));
		if (ehTitular(paciente, login) || representacaoRepository.existeVigente(pacienteId, login, LocalDateTime.now(clock))) {
			return paciente;
		}
		throw new AccessDeniedException(SEM_PERMISSAO);
	}

	public boolean ehTitular(Paciente paciente, String login) {
		return paciente.getConta() != null && paciente.getConta().getLogin().equals(login);
	}

	@Transactional(readOnly = true)
	public List<PacienteGerenciadoResponse> gerenciados(String login) {
		List<PacienteGerenciadoResponse> gerenciados = new ArrayList<>();
		pacienteRepository.findByContaLogin(login).ifPresent(proprio ->
				gerenciados.add(new PacienteGerenciadoResponse(proprio.getId(), proprio.getNome(), Relacao.TITULAR)));
		for (Paciente representado : representacaoRepository.representadosVigentes(login, LocalDateTime.now(clock))) {
			gerenciados.add(new PacienteGerenciadoResponse(representado.getId(), representado.getNome(),
					Relacao.REPRESENTANTE_LEGAL));
		}
		return gerenciados;
	}
}
