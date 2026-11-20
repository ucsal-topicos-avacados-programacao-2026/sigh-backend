package com.sigh.sigh.project.leito.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.leito.dto.FiltroLeitoDTO;
import com.sigh.sigh.project.leito.dto.LeitoRequestDTO;
import com.sigh.sigh.project.leito.dto.LeitoResponseDTO;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import com.sigh.sigh.project.leito.repository.LeitoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeitoService {

	private final LeitoRepository leitoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final VinculoService vinculoService;

	@Transactional
	public LeitoResponseDTO cadastrar(LeitoRequestDTO dados, String login) {
		vinculoService.exigirVinculoAtivo(login, dados.instituicaoId(), Perfil.ADMINISTRATIVO);
		Instituicao instituicao = instituicaoRepository.findById(dados.instituicaoId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Instituição não encontrada."));
		if (leitoRepository.existsByInstituicaoIdAndCodigo(instituicao.getId(), dados.codigo())) {
			throw new RegraNegocioException("Já existe leito com esse código na instituição.");
		}
		Leito leito = new Leito();
		leito.setInstituicao(instituicao);
		leito.setCodigo(dados.codigo());
		leito.setSetor(dados.setor());
		leito.setCategoria(dados.categoria());
		leito.setStatus(StatusLeito.LIVRE);
		return LeitoResponseDTO.de(leitoRepository.save(leito));
	}

	@Transactional(readOnly = true)
	public Page<LeitoResponseDTO> buscar(FiltroLeitoDTO filtro, Pageable paginacao) {
		return leitoRepository
				.buscar(filtro.instituicaoId(), filtro.categoria(), filtro.status(), paginacao)
				.map(LeitoResponseDTO::de);
	}

	@Transactional
	public LeitoResponseDTO alterarStatus(UUID leitoId, StatusLeito novoStatus, String login) {
		Leito leito = leitoRepository.findComBloqueioById(leitoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Leito não encontrado."));
		vinculoService.exigirVinculoAtivo(login, leito.getInstituicao().getId(), Perfil.ADMINISTRATIVO);
		validarAlteracaoManual(leito, novoStatus);
		leito.setStatus(novoStatus);
		return LeitoResponseDTO.de(leito);
	}

	private void validarAlteracaoManual(Leito leito, StatusLeito novoStatus) {
		if (novoStatus == StatusLeito.OCUPADO) {
			throw new RegraNegocioException("O leito só fica ocupado por internação ou transferência.");
		}
		if (leito.getStatus() == StatusLeito.OCUPADO) {
			throw new RegraNegocioException("Leito ocupado só é desocupado por alta ou transferência.");
		}
	}
}
