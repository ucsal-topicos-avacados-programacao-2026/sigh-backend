package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.service.HistoricoAutorizadoService;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContextoTriagemService {

	private static final String FINALIDADE_DO_ACESSO = "Triagem com apoio de IA";

	private final TriagemRepository triagemRepository;
	private final HistoricoAutorizadoService historicoAutorizadoService;
	private final IAProperties iaProperties;
	private final VinculoService vinculoService;

	@Transactional
	public CasoTriagemDTO montarCaso(UUID triagemId, String loginEnfermeiro) {
		Triagem triagem = triagemRepository.findById(triagemId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Triagem não encontrada."));
		vinculoService.exigirVinculoAtivo(loginEnfermeiro, triagem.getAtendimento().getInstituicao().getId(),
				Perfil.ENFERMEIRO);
		if (triagem.getCorConfirmada() != null) {
			throw new RegraNegocioException("A classificação desta triagem já foi confirmada.");
		}
		List<RegistroClinico> historico = historicoAutorizadoService.consultar(triagem.getAtendimento(),
				new ConsultaHistoricoDTO(loginEnfermeiro, Perfil.ENFERMEIRO, FINALIDADE_DO_ACESSO,
						iaProperties.limiteRegistrosHistorico()));
		Set<UUID> registrosUsados = historico.stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		return new CasoTriagemDTO(triagem.getId(), DescricaoCasoTriagem.montar(triagem, historico), registrosUsados);
	}
}
