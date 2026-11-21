package com.sigh.sigh.project.paciente.controller;

import com.sigh.sigh.project.paciente.dto.AdmissaoRequest;
import com.sigh.sigh.project.paciente.dto.AdmissaoResponse;
import com.sigh.sigh.project.paciente.service.AdmissaoPacienteService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacienteControllerTest {

	private final AdmissaoPacienteService servico = mock(AdmissaoPacienteService.class);
	private final PacienteController controller = new PacienteController(servico);
	private final TestingAuthenticationToken autenticacao = new TestingAuthenticationToken("admin", null);
	private final AdmissaoRequest requisicao = new AdmissaoRequest("52998224725", "Nome", null, null,
			UUID.randomUUID(), "Motivo");

	@Test
	void repeteUmaVezQuandoOCpfFoiCadastradoAoMesmoTempo() {
		AdmissaoResponse vinculado = new AdmissaoResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false);
		when(servico.admitir(requisicao, "admin")).thenThrow(violacao("23505")).thenReturn(vinculado);

		assertThat(controller.admitir(requisicao, autenticacao)).isEqualTo(vinculado);
		verify(servico, times(2)).admitir(requisicao, "admin");
	}

	@Test
	void naoRepeteOutrasViolacoesDeIntegridade() {
		when(servico.admitir(requisicao, "admin")).thenThrow(violacao("23503"));

		assertThatThrownBy(() -> controller.admitir(requisicao, autenticacao))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(servico, times(1)).admitir(requisicao, "admin");
	}

	@Test
	void repeteNoMaximoUmaVez() {
		when(servico.admitir(requisicao, "admin")).thenThrow(violacao("23505"), violacao("23505"));

		assertThatThrownBy(() -> controller.admitir(requisicao, autenticacao))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(servico, times(2)).admitir(requisicao, "admin");
	}

	private static DataIntegrityViolationException violacao(String sqlState) {
		return new DataIntegrityViolationException("falha", new SQLException("detalhe", sqlState));
	}
}
