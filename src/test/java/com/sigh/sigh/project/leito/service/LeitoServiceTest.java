package com.sigh.sigh.project.leito.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.leito.dto.LeitoRequestDTO;
import com.sigh.sigh.project.leito.dto.LeitoResponseDTO;
import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import com.sigh.sigh.project.leito.repository.LeitoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeitoServiceTest {

	@Mock
	private LeitoRepository leitoRepository;

	@Mock
	private InstituicaoRepository instituicaoRepository;

	@InjectMocks
	private LeitoService leitoService;

	@Test
	void leitoCadastradoComecaLivre() {
		Instituicao instituicao = novaInstituicao();
		when(instituicaoRepository.findById(instituicao.getId())).thenReturn(Optional.of(instituicao));
		when(leitoRepository.save(any(Leito.class))).thenAnswer(chamada -> chamada.getArgument(0));

		LeitoResponseDTO leito = leitoService.cadastrar(
				new LeitoRequestDTO(instituicao.getId(), "UTI-01", "UTI adulto", CategoriaLeito.UTI));

		assertThat(leito.status()).isEqualTo(StatusLeito.LIVRE);
		assertThat(leito.categoria()).isEqualTo(CategoriaLeito.UTI);
	}

	@Test
	void codigoRepetidoNaMesmaInstituicaoEhBloqueado() {
		Instituicao instituicao = novaInstituicao();
		when(instituicaoRepository.findById(instituicao.getId())).thenReturn(Optional.of(instituicao));
		when(leitoRepository.existsByInstituicaoIdAndCodigo(instituicao.getId(), "UTI-01")).thenReturn(true);

		assertThatThrownBy(() -> leitoService.cadastrar(
				new LeitoRequestDTO(instituicao.getId(), "UTI-01", "UTI adulto", CategoriaLeito.UTI)))
				.isInstanceOf(RegraNegocioException.class);
		verify(leitoRepository, never()).save(any());
	}

	@Test
	void instituicaoInexistenteRetornaNaoEncontrado() {
		UUID instituicaoId = UUID.randomUUID();
		when(instituicaoRepository.findById(instituicaoId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> leitoService.cadastrar(
				new LeitoRequestDTO(instituicaoId, "ENF-01", "Clínica médica", CategoriaLeito.ENFERMARIA)))
				.isInstanceOf(RecursoNaoEncontradoException.class);
	}

	@Test
	void leitoHigienizadoPodeVoltarAFicarLivre() {
		Leito leito = novoLeito(StatusLeito.EM_HIGIENIZACAO);
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		leitoService.alterarStatus(leito.getId(), StatusLeito.LIVRE);

		assertThat(leito.getStatus()).isEqualTo(StatusLeito.LIVRE);
	}

	@Test
	void leitoNaoFicaOcupadoPorAlteracaoManual() {
		Leito leito = novoLeito(StatusLeito.LIVRE);
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		assertThatThrownBy(() -> leitoService.alterarStatus(leito.getId(), StatusLeito.OCUPADO))
				.isInstanceOf(RegraNegocioException.class);
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.LIVRE);
	}

	@Test
	void leitoOcupadoNaoEhLiberadoPorAlteracaoManual() {
		Leito leito = novoLeito(StatusLeito.OCUPADO);
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		assertThatThrownBy(() -> leitoService.alterarStatus(leito.getId(), StatusLeito.LIVRE))
				.isInstanceOf(RegraNegocioException.class);
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.OCUPADO);
	}

	private Instituicao novaInstituicao() {
		Instituicao instituicao = new Instituicao();
		instituicao.setId(UUID.randomUUID());
		return instituicao;
	}

	private Leito novoLeito(StatusLeito status) {
		Leito leito = new Leito();
		leito.setId(UUID.randomUUID());
		leito.setInstituicao(novaInstituicao());
		leito.setStatus(status);
		return leito;
	}
}
