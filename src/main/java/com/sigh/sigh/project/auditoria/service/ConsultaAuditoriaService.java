package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO;
import com.sigh.sigh.project.auditoria.dto.FiltroAuditoriaDTO;
import com.sigh.sigh.project.auditoria.dto.IndicadorInstituicaoDTO;
import com.sigh.sigh.project.auditoria.dto.PeriodoDTO;
import com.sigh.sigh.project.auditoria.dto.RegistroAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultaAuditoriaService {

	private final RegistroAuditoriaRepository registroAuditoriaRepository;

	@Transactional(readOnly = true)
	public Page<AcessoDoPacienteDTO> consultarAcessosDoPaciente(String loginPaciente, Pageable paginacao) {
		return registroAuditoriaRepository.buscarAcessosDoPaciente(loginPaciente, paginacao);
	}

	@Transactional(readOnly = true)
	public Page<RegistroAuditoriaResponseDTO> consultarRegistros(FiltroAuditoriaDTO filtro, Pageable paginacao) {
		PeriodoDTO periodo = validar(filtro.periodo());
		return registroAuditoriaRepository.buscarRegistros(
				filtro.instituicaoId(),
				filtro.emergencia(),
				periodo.inicioDoPrimeiroDia(),
				periodo.inicioDoDiaSeguinteAoFim(),
				paginacao);
	}

	@Transactional(readOnly = true)
	public List<IndicadorInstituicaoDTO> consultarIndicadores(PeriodoDTO periodo) {
		validar(periodo);
		return registroAuditoriaRepository.contarAcessosPorInstituicao(
				periodo.inicioDoPrimeiroDia(),
				periodo.inicioDoDiaSeguinteAoFim());
	}

	private PeriodoDTO validar(PeriodoDTO periodo) {
		if (periodo.terminaAntesDeComecar()) {
			throw new RegraNegocioException("A data final do período é anterior à inicial.");
		}
		return periodo;
	}
}
