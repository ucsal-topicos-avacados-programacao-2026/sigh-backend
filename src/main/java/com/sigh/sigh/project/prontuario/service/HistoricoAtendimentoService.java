package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.DadosInvalidosException;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.dto.HistoricoDoAtendimentoDTO;
import com.sigh.sigh.project.prontuario.dto.RegistroDoHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class HistoricoAtendimentoService {

	static final String FINALIDADE_CONSULTA = "Consulta do histórico no atendimento";
	static final String FINALIDADE_EMERGENCIA = "Acesso de emergência";
	static final String FINALIDADE_TENTATIVA_RECUSADA = "Tentativa de acesso de emergência recusada";
	static final int JUSTIFICATIVA_MINIMA = 20;
	static final Set<CorRisco> GRAVES = Set.of(CorRisco.VERMELHO, CorRisco.LARANJA);

	private final AtendimentoRepository atendimentoRepository;
	private final HistoricoAutorizadoService historicoAutorizadoService;
	private final VinculoService vinculoService;
	private final TriagemRepository triagemRepository;
	private final RegistroAuditoriaService registroAuditoriaService;
	private final Clock clock;
	private final int limiteDeRegistros;
	private final Duration janelaDeEmergencia;

	public HistoricoAtendimentoService(AtendimentoRepository atendimentoRepository,
			HistoricoAutorizadoService historicoAutorizadoService, VinculoService vinculoService,
			TriagemRepository triagemRepository, RegistroAuditoriaService registroAuditoriaService, Clock clock,
			@Value("${sigh.historico.limite-registros}") int limiteDeRegistros,
			@Value("${sigh.emergencia.janela}") Duration janelaDeEmergencia) {
		this.atendimentoRepository = atendimentoRepository;
		this.historicoAutorizadoService = historicoAutorizadoService;
		this.vinculoService = vinculoService;
		this.triagemRepository = triagemRepository;
		this.registroAuditoriaService = registroAuditoriaService;
		this.clock = clock;
		this.limiteDeRegistros = limiteDeRegistros;
		this.janelaDeEmergencia = janelaDeEmergencia;
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

	@Transactional
	public HistoricoDoAtendimentoDTO acessarEmEmergencia(UUID atendimentoId, String justificativa, String login) {
		Atendimento atendimento = buscar(atendimentoId);
		Perfil perfil = perfilNoAtendimento(atendimento, login);
		if (atendimento.getStatus() == StatusAtendimento.ENCERRADO) {
			throw new RegraNegocioException("Acesso de emergência só vale para atendimento em andamento.");
		}
		if (!atendimento.getInstituicao().isCredenciada()) {
			throw new RegraNegocioException("A instituição não está credenciada na rede.");
		}
		String texto = justificativa.strip();
		try {
			exigirRiscoImediato(atendimento, login);
		} catch (EmergenciaRecusadaException recusa) {
			registrarTentativa(atendimento, login, perfil, texto, recusa);
			throw recusa;
		}
		if (texto.length() < JUSTIFICATIVA_MINIMA) {
			throw new DadosInvalidosException(Map.of("justificativa", "descreva a emergência com 20 a 2000 caracteres"));
		}
		List<RegistroClinico> registros = historicoAutorizadoService.consultarEmEmergencia(atendimento,
				new ConsultaHistoricoDTO(login, perfil, FINALIDADE_EMERGENCIA, limiteDeRegistros), texto);
		return new HistoricoDoAtendimentoDTO(
				paraDTO(registros, historicoAutorizadoService.restritos(atendimento.getProntuario())), false, true);
	}

	private void registrarTentativa(Atendimento atendimento, String login, Perfil perfil, String justificativa,
			EmergenciaRecusadaException recusa) {
		try {
			registroAuditoriaService.registrarTentativaRecusada(new AcessoProntuarioDTO(
					atendimento.getProntuario().getId(), login, perfil, atendimento.getInstituicao().getId(),
					FINALIDADE_TENTATIVA_RECUSADA + ": " + recusa.motivo().descricao(), false,
					justificativa.isEmpty() ? null : justificativa, Set.of()));
		} catch (RuntimeException falha) {
			recusa.addSuppressed(falha);
		}
	}

	private void exigirRiscoImediato(Atendimento atendimento, String login) {
		Triagem classificacao = triagemRepository
				.findFirstByAtendimentoIdAndCorConfirmadaIsNotNullOrderByConfirmadaEmDescIdDesc(atendimento.getId())
				.filter(triagem -> GRAVES.contains(triagem.getCorConfirmada()) && triagem.getConfirmador() != null)
				.orElseThrow(() -> new EmergenciaRecusadaException(EmergenciaRecusadaException.Motivo.SEM_RISCO_IMEDIATO,
						"Acesso de emergência só vale para atendimento com classificação de risco vermelha ou laranja"
								+ " confirmada."));
		if (login.equals(classificacao.getConfirmador().getLogin())) {
			throw new EmergenciaRecusadaException(EmergenciaRecusadaException.Motivo.QUEM_CLASSIFICOU,
					"Quem confirmou a classificação de risco não pode usar o acesso de emergência com base nela:"
							+ " outro profissional precisa fazê-lo.");
		}
		if (LocalDateTime.now(clock).isAfter(classificacao.getConfirmadaEm().plus(janelaDeEmergencia))) {
			throw new EmergenciaRecusadaException(EmergenciaRecusadaException.Motivo.FORA_DA_JANELA,
					"O acesso de emergência só vale até " + duracao(janelaDeEmergencia)
							+ " depois da classificação vermelha ou laranja.");
		}
	}

	private static String duracao(Duration janela) {
		return janela.toMinutes() % 60 == 0 ? janela.toHours() + " horas" : janela.toMinutes() + " minutos";
	}

	private Atendimento buscar(UUID atendimentoId) {
		return atendimentoRepository.findById(atendimentoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
	}

	private Perfil perfilNoAtendimento(Atendimento atendimento, String login) {
		return vinculoService.perfilHabilitado(login, atendimento.getInstituicao().getId(), Perfil.MEDICO,
				Perfil.ENFERMEIRO);
	}

	private static List<RegistroDoHistoricoDTO> paraDTO(List<RegistroClinico> registros, Set<UUID> restritos) {
		return registros.stream().map(registro -> RegistroDoHistoricoDTO.de(registro, restritos)).toList();
	}
}
