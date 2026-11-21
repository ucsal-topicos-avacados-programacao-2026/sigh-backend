package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.prontuario.dto.AtendimentoEmAndamentoDTO;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtendimentosEmAndamentoService {

	private final AtendimentoRepository atendimentoRepository;
	private final TriagemRepository triagemRepository;
	private final VinculoService vinculoService;

	@Transactional(readOnly = true)
	public List<AtendimentoEmAndamentoDTO> emAndamento(UUID instituicaoId, String login) {
		vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.MEDICO, Perfil.ENFERMEIRO);
		return atendimentoRepository.emAndamento(instituicaoId).stream().map(atendimento -> {
			var cor = triagemRepository
					.findFirstByAtendimentoIdAndCorConfirmadaIsNotNullOrderByConfirmadaEmDescIdDesc(atendimento.getId())
					.map(Triagem::getCorConfirmada).orElse(null);
			var medico = atendimento.getMedicoResponsavel();
			return new AtendimentoEmAndamentoDTO(atendimento.getId(), atendimento.getProntuario().getPaciente().getNome(),
					atendimento.getMotivo(), atendimento.getChegadaEm(), atendimento.getInicioClinicoEm(), cor,
					medico == null ? null : medico.getNome(), medico != null && medico.getLogin().equals(login));
		}).toList();
	}
}
