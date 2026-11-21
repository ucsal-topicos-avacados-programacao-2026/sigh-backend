package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.config.IAProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AquecimentoDaIATest {

	private final ClienteIA clienteIA = mock(ClienteIA.class);
	private final FilaDeResumosIA fila = mock(FilaDeResumosIA.class);

	@Test
	void comOAquecimentoLigadoCarregaOModeloPelaFila() {
		filaQueRodaNaHora();

		new AquecimentoDaIA(configuracao(true), clienteIA, fila, Clock.systemUTC()).aquecer();

		verify(fila).enfileirar(any());
		verify(clienteIA).aquecer();
	}

	@Test
	void comOAquecimentoDesligadoNaoChamaAIA() {
		new AquecimentoDaIA(configuracao(false), clienteIA, fila, Clock.systemUTC()).aquecer();

		verify(fila, never()).enfileirar(any());
		verify(clienteIA, never()).aquecer();
	}

	@Test
	void falhaDaIAOuFilaCheiaNaoDerrubamASubida() {
		filaQueRodaNaHora();
		doThrow(new IAIndisponivelException("O serviço de IA não respondeu.")).when(clienteIA).aquecer();
		assertThatCode(() -> new AquecimentoDaIA(configuracao(true), clienteIA, fila, Clock.systemUTC()).aquecer()).doesNotThrowAnyException();

		doThrow(new RejectedExecutionException()).when(fila).enfileirar(any());
		assertThatCode(() -> new AquecimentoDaIA(configuracao(true), clienteIA, fila, Clock.systemUTC()).aquecer()).doesNotThrowAnyException();
	}

	private void filaQueRodaNaHora() {
		doAnswer(chamada -> {
			chamada.<Runnable>getArgument(0).run();
			return null;
		}).when(fila).enfileirar(any());
	}

	private static IAProperties configuracao(boolean aquecer) {
		return new IAProperties("http://ia.teste", "modelo-teste", null, 0, Duration.ofSeconds(5), 20,
				new ByteArrayResource(new byte[0]), new ByteArrayResource(new byte[0]), Duration.ofSeconds(10), 5, aquecer);
	}
}
