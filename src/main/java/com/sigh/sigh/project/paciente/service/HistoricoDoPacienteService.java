package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.prontuario.dto.RegistroDoHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O paciente ou representante consulta o prontuário inteiro (caso de uso U1), com a marcação do que
 * está restrito, para decidir o que restringir ou liberar (RF-009).
 */
@Service
@RequiredArgsConstructor
public class HistoricoDoPacienteService {

	private final TitularidadeService titularidadeService;
	private final ProntuarioRepository prontuarioRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<RegistroDoHistoricoDTO> consultar(UUID pacienteId, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		Prontuario prontuario = prontuarioRepository.findByPacienteId(paciente.getId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Prontuário não encontrado."));
		Set<UUID> restritos = Set.copyOf(registroClinicoRepository.restritos(prontuario.getId(), LocalDateTime.now(clock)));
		return registroClinicoRepository.historicoCompleto(prontuario.getId()).stream()
				.map(registro -> RegistroDoHistoricoDTO.de(registro, restritos)).toList();
	}
}
