package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.FiltroAuditoriaDTO;
import com.sigh.sigh.project.auditoria.dto.PeriodoDTO;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultaAuditoriaServiceTest {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 19);

	@Mock
	private RegistroAuditoriaRepository registroAuditoriaRepository;

	@InjectMocks
	private ConsultaAuditoriaService consultaAuditoriaService;

	@Test
	void periodoInvertidoEhRecusado() {
		PeriodoDTO invertido = new PeriodoDTO(HOJE, HOJE.minusDays(1));

		assertThatThrownBy(() -> consultaAuditoriaService.consultarIndicadores(invertido))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void periodoIncluiODiaFinalInteiro() {
		FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(null, null, HOJE, HOJE);

		consultaAuditoriaService.consultarRegistros(filtro, Pageable.unpaged());

		verify(registroAuditoriaRepository).buscarRegistros(null, null,
				HOJE.atStartOfDay(), HOJE.plusDays(1).atStartOfDay(), Pageable.unpaged());
	}
}
