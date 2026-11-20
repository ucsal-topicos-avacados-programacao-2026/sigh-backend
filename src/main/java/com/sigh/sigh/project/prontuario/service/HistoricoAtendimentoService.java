package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.dto.HistoricoDoAtendimentoDTO;
import com.sigh.sigh.project.prontuario.dto.RegistroDoHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consulta do histórico pelo médico ou enfermeiro do atendimento, comum ou de emergência (RF-009, RF-010). */
@Service
public class HistoricoAtendimentoService {

	static final String FINALIDADE_CONSULTA = "Consulta do histórico no atendimento";
	static final String FINALIDADE_EMERGENCIA = "Acesso de emergência";

	private final AtendimentoRepository atendimentoRepository;
	private final HistoricoAutorizadoService historicoAutorizadoService;
	private final VinculoService vinculoService;
	private final int limiteDeRegistros;

	public HistoricoAtendimentoService(AtendimentoRepository atendimentoRepository,
			HistoricoAutorizadoService historicoAutorizadoService, VinculoService vinculoService,
			@Value("${sigh.historico.limite-registros}") int limiteDeRegistros) {
		this.atendimentoRepository = atendimentoRepository;
		this.historicoAutorizadoService = historicoAutorizadoService;
		this.vinculoService = vinculoService;
		this.limiteDeRegistros = limiteDeRegistros;
	}

	@Transactional
	public HistoricoDoAtendimentoDTO consultar(UUID atendimentoId, String login) {
		Atendimento atendimento = buscar(atendimentoId);
		Perfil perfil = perfilNoAtendimento(atendimento, login);
		List<RegistroClinico> registros = historicoAutorizadoService.consultar(atendimento,
				new ConsultaHistoricoDTO(login, perfil, FINALIDADE_CONSULTA, limiteDeRegistros));
		return new HistoricoDoAtendimentoDTO(paraDTO(registros, historicoAutorizadoService.restritos(atendimento.getProntuario())),
				historicoAutorizadoService.possuiInformacoesRestritas(atendimento), false);
	}

	/**
	 * Só para atendimento em andamento: depois de encerrado não há mais risco iminente a justificar.
	 * Os registros restritos voltam marcados, para o profissional saber o que o paciente quis proteger.
	 */
	@Transactional
	public HistoricoDoAtendimentoDTO acessarEmEmergencia(UUID atendimentoId, String justificativa, String login) {
		Atendimento atendimento = buscar(atendimentoId);
		Perfil perfil = perfilNoAtendimento(atendimento, login);
		if (atendimento.getStatus() == StatusAtendimento.ENCERRADO) {
			throw new RegraNegocioException("Acesso de emergência só vale para atendimento em andamento.");
		}
		List<RegistroClinico> registros = historicoAutorizadoService.consultarEmEmergencia(atendimento,
				new ConsultaHistoricoDTO(login, perfil, FINALIDADE_EMERGENCIA, limiteDeRegistros), justificativa.strip());
		return new HistoricoDoAtendimentoDTO(
				paraDTO(registros, historicoAutorizadoService.restritos(atendimento.getProntuario())), false, true);
	}

	private Atendimento buscar(UUID atendimentoId) {
		return atendimentoRepository.findById(atendimentoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
	}

	/** Médico ou enfermeiro com vínculo ativo na instituição do atendimento ("profissional autorizado"). */
	private Perfil perfilNoAtendimento(Atendimento atendimento, String login) {
		return vinculoService.perfilHabilitado(login, atendimento.getInstituicao().getId(), Perfil.MEDICO,
				Perfil.ENFERMEIRO);
	}

	private static List<RegistroDoHistoricoDTO> paraDTO(List<RegistroClinico> registros, Set<UUID> restritos) {
		return registros.stream().map(registro -> RegistroDoHistoricoDTO.de(registro, restritos)).toList();
	}
}
