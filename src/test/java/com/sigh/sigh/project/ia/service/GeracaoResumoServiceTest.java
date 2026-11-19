package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeracaoResumoServiceTest {

	private static final String LOGIN_MEDICO = "medico.teste";
	private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
	private static final CasoResumoDTO CASO = new CasoResumoDTO(UUID.randomUUID(), "- registro autorizado");

	@Mock
	private ResumoHistoricoService resumoHistoricoService;

	@Mock
	private ClienteIA clienteIA;

	@InjectMocks
	private GeracaoResumoService geracaoResumoService;

	@Test
	void textoDaIAViraResumoGerado() {
		when(resumoHistoricoService.solicitar(ATENDIMENTO_ID, LOGIN_MEDICO)).thenReturn(CASO);
		when(clienteIA.resumirHistorico(CASO.historico())).thenReturn("Alergias: dipirona.");

		geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO);

		verify(resumoHistoricoService).concluir(CASO.resumoId(), "Alergias: dipirona.");
		verify(resumoHistoricoService, never()).registrarFalha(any());
	}

	@Test
	void falhaDaIAMarcaOResumoComoFalhouEAvisaOMedico() {
		when(resumoHistoricoService.solicitar(ATENDIMENTO_ID, LOGIN_MEDICO)).thenReturn(CASO);
		when(clienteIA.resumirHistorico(CASO.historico()))
				.thenThrow(new IAIndisponivelException("O serviço de IA não respondeu. Siga sem a sugestão da IA."));

		assertThatThrownBy(() -> geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO))
				.isInstanceOf(IAIndisponivelException.class);
		verify(resumoHistoricoService).registrarFalha(CASO.resumoId());
		verify(resumoHistoricoService, never()).concluir(any(), any());
	}
}
