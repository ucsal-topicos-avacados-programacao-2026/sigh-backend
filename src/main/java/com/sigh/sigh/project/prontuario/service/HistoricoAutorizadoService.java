package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoricoAutorizadoService {

	private final ConsentimentoRepository consentimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final RegistroAuditoriaService registroAuditoriaService;

	@Transactional
	public List<RegistroClinico> consultar(Atendimento atendimento, ConsultaHistoricoDTO consulta) {
		Prontuario prontuario = atendimento.getProntuario();
		if (!pacienteAutorizouCompartilhamento(prontuario.getPaciente().getId())) {
			return List.of();
		}
		List<RegistroClinico> historico = registroClinicoRepository.buscarHistoricoSemRestricao(
				prontuario.getId(), LocalDateTime.now(), Limit.of(consulta.limiteDeRegistros()));
		if (!historico.isEmpty()) {
			registrarAcesso(atendimento, consulta, historico);
		}
		return historico;
	}

	private boolean pacienteAutorizouCompartilhamento(UUID pacienteId) {
		return consentimentoRepository.findFirstByPacienteIdAndTipoOrderByVersaoDesc(pacienteId, TipoConsentimento.AMPLO)
				.map(consentimento -> consentimento.getDecisao() == DecisaoConsentimento.AUTORIZADO)
				.orElse(false);
	}

	private void registrarAcesso(Atendimento atendimento, ConsultaHistoricoDTO consulta, List<RegistroClinico> historico) {
		Set<UUID> registrosAcessados = historico.stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(
				atendimento.getProntuario().getId(),
				consulta.loginProfissional(),
				consulta.perfil(),
				atendimento.getInstituicao().getId(),
				consulta.finalidade(),
				false,
				null,
				registrosAcessados));
	}
}
