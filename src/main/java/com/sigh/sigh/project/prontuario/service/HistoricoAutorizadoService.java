package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.paciente.service.ConsentimentoService;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Único ponto que decide quais registros clínicos um atendimento pode ver. Triagem com IA, resumo
 * por IA e a consulta do profissional passam por aqui, então a mesma regra vale para todos (RF-009):
 * <ul>
 *   <li>a instituição sempre vê os registros que ela mesma produziu;</li>
 *   <li>os das outras instituições dependem do consentimento amplo (RF-002, RN-007);</li>
 *   <li>registros restritos pelo paciente ficam de fora, salvo autorização adicional para a instituição;</li>
 *   <li>o acesso de emergência (RN-003) mostra tudo, depois de registrar a justificativa.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HistoricoAutorizadoService {

	private final ConsentimentoService consentimentoService;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final RegistroAuditoriaService registroAuditoriaService;
	private final Clock clock;

	/** Histórico visível para o atendimento, com o acesso registrado na auditoria (RF-008). */
	@Transactional
	public List<RegistroClinico> consultar(Atendimento atendimento, ConsultaHistoricoDTO consulta) {
		Prontuario prontuario = atendimento.getProntuario();
		List<RegistroClinico> historico = registroClinicoRepository.historicoVisivel(prontuario.getId(),
				atendimento.getInstituicao().getId(), compartilhadoCom(atendimento), LocalDateTime.now(clock),
				Limit.of(consulta.limiteDeRegistros()));
		if (!historico.isEmpty()) {
			registrarAcesso(atendimento, consulta, historico, false, null);
		}
		return historico;
	}

	/** Se há registros escondidos só pela restrição do paciente, que um acesso de emergência mostraria. */
	@Transactional(readOnly = true)
	public boolean possuiInformacoesRestritas(Atendimento atendimento) {
		return registroClinicoRepository.contarRestritosOcultos(atendimento.getProntuario().getId(),
				atendimento.getInstituicao().getId(), compartilhadoCom(atendimento), LocalDateTime.now(clock)) > 0;
	}

	/**
	 * Acesso de emergência (RF-010, RN-003): a justificativa é gravada na auditoria antes de qualquer
	 * registro ser devolvido. Se a gravação falhar, a transação é desfeita e nada é exibido.
	 */
	@Transactional
	public List<RegistroClinico> consultarEmEmergencia(Atendimento atendimento, ConsultaHistoricoDTO consulta,
			String justificativa) {
		List<RegistroClinico> historico = registroClinicoRepository.historicoCompleto(atendimento.getProntuario().getId());
		registrarAcesso(atendimento, consulta, historico, true, justificativa);
		// Grava agora: se a auditoria falhar, a exceção sai antes de qualquer dado ser devolvido.
		registroClinicoRepository.flush();
		return historico;
	}

	/**
	 * Se todos os registros informados continuam visíveis para o atendimento. Um resumo por IA feito
	 * antes de uma restrição ou revogação não pode continuar mostrando o que ela passou a esconder.
	 */
	@Transactional(readOnly = true)
	public boolean continuamVisiveis(Atendimento atendimento, Set<UUID> registros) {
		if (registros.isEmpty()) {
			return true;
		}
		return registroClinicoRepository.visiveisEntre(registros, atendimento.getInstituicao().getId(),
				compartilhadoCom(atendimento), LocalDateTime.now(clock)).size() == registros.size();
	}

	/** Registros do prontuário cobertos por restrição vigente do paciente. */
	@Transactional(readOnly = true)
	public Set<UUID> restritos(Prontuario prontuario) {
		return Set.copyOf(registroClinicoRepository.restritos(prontuario.getId(), LocalDateTime.now(clock)));
	}

	private boolean compartilhadoCom(Atendimento atendimento) {
		return consentimentoService.podeCompartilharCom(atendimento.getProntuario().getPaciente().getId(),
				atendimento.getInstituicao().getId());
	}

	private void registrarAcesso(Atendimento atendimento, ConsultaHistoricoDTO consulta, List<RegistroClinico> historico,
			boolean emergencia, String justificativa) {
		Set<UUID> registrosAcessados = historico.stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(
				atendimento.getProntuario().getId(),
				consulta.loginProfissional(),
				consulta.perfil(),
				atendimento.getInstituicao().getId(),
				consulta.finalidade(),
				emergencia,
				justificativa,
				registrosAcessados));
	}
}
