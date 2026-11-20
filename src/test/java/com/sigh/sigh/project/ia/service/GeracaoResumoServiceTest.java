package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import com.sigh.sigh.project.ia.entity.StatusResumo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A fila simulada roda a tarefa na hora; o teste da fila de verdade fica em ResumoEmSegundoPlanoTest. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeracaoResumoServiceTest {

	private static final String LOGIN_MEDICO = "medico.teste";
	private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
	private static final CasoResumoDTO CASO = new CasoResumoDTO(UUID.randomUUID(), "- registro autorizado");
	private static final ResumoHistoricoResponseDTO EM_GERACAO = new ResumoHistoricoResponseDTO(CASO.resumoId(),
			ATENDIMENTO_ID, StatusResumo.SOLICITADO, null, null, null, Set.of(), "em geração");

	@Mock
	private ResumoHistoricoService resumoHistoricoService;

	@Mock
	private ClienteIA clienteIA;

	@Mock
	private FilaDeResumosIA filaDeResumos;

	@InjectMocks
	private GeracaoResumoService geracaoResumoService;

	@BeforeEach
	void filaQueRodaNaHora() {
		when(resumoHistoricoService.solicitar(ATENDIMENTO_ID, LOGIN_MEDICO)).thenReturn(CASO);
		when(resumoHistoricoService.emGeracao(CASO.resumoId())).thenReturn(EM_GERACAO);
		when(resumoHistoricoService.continuaAutorizado(CASO.resumoId())).thenReturn(true);
		doAnswer(chamada -> {
			chamada.<Runnable>getArgument(1).run();
			return null;
		}).when(filaDeResumos).enfileirar(eq(CASO.resumoId()), any());
	}

	@Test
	void pedidoVoltaComOResumoEmGeracaoEATarefaConcluiComOTextoDaIA() {
		when(clienteIA.resumirHistorico(CASO.historico())).thenReturn("Alergias: dipirona.");

		assertThat(geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO)).isEqualTo(EM_GERACAO);

		verify(resumoHistoricoService).concluir(CASO.resumoId(), "Alergias: dipirona.");
		verify(resumoHistoricoService, never()).registrarFalha(any());
	}

	@Test
	void falhaDaIAMarcaOResumoComoFalhoSemDerrubarOPedido() {
		when(clienteIA.resumirHistorico(CASO.historico()))
				.thenThrow(new IAIndisponivelException("O serviço de IA não respondeu. Siga sem a sugestão da IA."));

		assertThat(geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO)).isEqualTo(EM_GERACAO);

		verify(resumoHistoricoService).registrarFalha(CASO.resumoId());
		verify(resumoHistoricoService, never()).concluir(any(), any());
	}

	@Test
	void autorizacaoQueMudouNaFilaNaoMandaOHistoricoAIA() {
		when(resumoHistoricoService.continuaAutorizado(CASO.resumoId())).thenReturn(false);

		geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO);

		verify(clienteIA, never()).resumirHistorico(any());
		verify(resumoHistoricoService).registrarFalha(CASO.resumoId());
	}

	@Test
	void falhaInesperadaTambemMarcaOResumoComoFalho() {
		when(clienteIA.resumirHistorico(CASO.historico())).thenThrow(new IllegalStateException("inesperada"));

		geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO);

		verify(resumoHistoricoService).registrarFalha(CASO.resumoId());
	}

	@Test
	void filaCheiaRecusaNaHoraEMarcaOResumoComoFalho() {
		doThrow(new RejectedExecutionException()).when(filaDeResumos).enfileirar(eq(CASO.resumoId()), any());

		assertThatThrownBy(() -> geracaoResumoService.gerar(ATENDIMENTO_ID, LOGIN_MEDICO))
				.isInstanceOf(IAIndisponivelException.class)
				.hasMessageContaining("fila");
		verify(resumoHistoricoService).registrarFalha(CASO.resumoId());
		verify(clienteIA, never()).resumirHistorico(any());
	}
}
