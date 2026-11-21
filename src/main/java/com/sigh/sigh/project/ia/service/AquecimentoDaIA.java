package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.ia.config.IAProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;

@Component
@RequiredArgsConstructor
public class AquecimentoDaIA {

	private static final Logger log = LoggerFactory.getLogger(AquecimentoDaIA.class);

	private final IAProperties iaProperties;
	private final ClienteIA clienteIA;
	private final FilaDeResumosIA filaDeResumos;
	private final Clock clock;

	@EventListener(ApplicationReadyEvent.class)
	public void aquecer() {
		if (!iaProperties.aquecerAoSubir()) {
			return;
		}
		try {
			filaDeResumos.enfileirar(this::carregarModelo);
		} catch (RejectedExecutionException filaCheia) {
			log.warn("Aquecimento da IA não enfileirado: fila dos resumos cheia.");
		}
	}

	void carregarModelo() {
		Instant inicio = Instant.now(clock);
		try {
			clienteIA.aquecer();
			log.info("Modelo de IA carregado em {} s.", Duration.between(inicio, Instant.now(clock)).toSeconds());
		} catch (RuntimeException falha) {
			log.warn("Aquecimento da IA falhou ({}); o primeiro pedido carregará o modelo.",
					falha.getClass().getSimpleName());
		}
	}
}
