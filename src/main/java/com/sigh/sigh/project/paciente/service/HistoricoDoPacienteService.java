package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.prontuario.dto.RegistroDoHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	private final RegistroAuditoriaService registroAuditoriaService;

	static final String FINALIDADE_REPRESENTANTE = "Consulta do histórico pelo representante legal";

	/** A leitura pelo representante legal fica na auditoria (RF-008); a do próprio titular, não. */
	@Transactional
	public List<RegistroDoHistoricoDTO> consultar(UUID pacienteId, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		Prontuario prontuario = prontuarioRepository.findByPacienteId(paciente.getId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Prontuário não encontrado."));
		Set<UUID> restritos = Set.copyOf(registroClinicoRepository.restritos(prontuario.getId(), LocalDateTime.now(clock)));
		List<RegistroClinico> historico = registroClinicoRepository.historicoCompleto(prontuario.getId());
		if (!titularidadeService.ehTitular(paciente, login)) {
			registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(prontuario.getId(), login, Perfil.PACIENTE,
					null, FINALIDADE_REPRESENTANTE, false, null,
					historico.stream().map(RegistroClinico::getId).collect(Collectors.toSet())));
		}
		return historico.stream().map(registro -> RegistroDoHistoricoDTO.de(registro, restritos)).toList();
	}
}
