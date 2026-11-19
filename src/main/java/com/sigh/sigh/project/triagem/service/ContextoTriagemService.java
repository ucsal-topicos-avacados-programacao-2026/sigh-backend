package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
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
public class ContextoTriagemService {

	private static final String FINALIDADE_DO_ACESSO = "Triagem com apoio de IA";

	private final TriagemRepository triagemRepository;
	private final ConsentimentoRepository consentimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final RegistroAuditoriaService registroAuditoriaService;
	private final IAProperties iaProperties;

	@Transactional
	public CasoTriagemDTO montarCaso(UUID triagemId, String loginEnfermeiro) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		if (triagem.getCorConfirmada() != null) {
			throw new RegraNegocioException("A classificação desta triagem já foi confirmada.");
		}
		Atendimento atendimento = triagem.getAtendimento();
		List<RegistroClinico> historico = buscarHistoricoAutorizado(atendimento.getProntuario());
		Set<UUID> registrosUsados = historico.stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		if (!registrosUsados.isEmpty()) {
			registrarAcessoAoHistorico(atendimento, registrosUsados, loginEnfermeiro);
		}
		return new CasoTriagemDTO(triagem.getId(), DescricaoCasoTriagem.montar(triagem, historico), registrosUsados);
	}

	private List<RegistroClinico> buscarHistoricoAutorizado(Prontuario prontuario) {
		if (!pacienteAutorizouCompartilhamento(prontuario.getPaciente().getId())) {
			return List.of();
		}
		return registroClinicoRepository.buscarHistoricoSemRestricao(
				prontuario.getId(), LocalDateTime.now(), Limit.of(iaProperties.limiteRegistrosHistorico()));
	}

	private boolean pacienteAutorizouCompartilhamento(UUID pacienteId) {
		return consentimentoRepository.findFirstByPacienteIdAndTipoOrderByVersaoDesc(pacienteId, TipoConsentimento.AMPLO)
				.map(consentimento -> consentimento.getDecisao() == DecisaoConsentimento.AUTORIZADO)
				.orElse(false);
	}

	private void registrarAcessoAoHistorico(Atendimento atendimento, Set<UUID> registrosUsados, String loginEnfermeiro) {
		registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(
				atendimento.getProntuario().getId(),
				loginEnfermeiro,
				Perfil.ENFERMEIRO,
				atendimento.getInstituicao().getId(),
				FINALIDADE_DO_ACESSO,
				false,
				null,
				registrosUsados));
	}
}
