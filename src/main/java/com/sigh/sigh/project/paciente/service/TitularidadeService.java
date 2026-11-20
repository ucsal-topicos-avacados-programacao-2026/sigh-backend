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

/**
 * Quem pode gerenciar os dados de um paciente: o próprio titular (conta vinculada ao cadastro) ou o
 * representante legal com representação vigente (RF-002, RF-009). Paciente inexistente e sem
 * permissão dão a mesma resposta, para não revelar quem está cadastrado.
 */
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
		boolean titular = paciente.getConta() != null && paciente.getConta().getLogin().equals(login);
		if (titular || representacaoRepository.existeVigente(pacienteId, login, LocalDateTime.now(clock))) {
			return paciente;
		}
		throw new AccessDeniedException(SEM_PERMISSAO);
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
