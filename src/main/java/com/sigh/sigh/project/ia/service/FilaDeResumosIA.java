package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.ia.config.IAProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fila dos resumos por IA: um por vez, porque o serviço de IA local atende um pedido por vez, e pedidos em
 * paralelo só esperariam lá, estourando o tempo-limite. Com a fila cheia ({@code sigh.ia.fila-maxima-resumos}),
 * o pedido é recusado na hora.
 * <p>
 * A fila fica na memória e sabe quais resumos estão nela ou em geração ({@link #pendente}). Um resumo
 * "em geração" que não está aqui não vai mais ser gerado: a tarefa terminou sem conseguir gravar o resultado,
 * ou a aplicação parou. A consulta o mostra como falho ({@link ResumoHistoricoService#consultar}), e a subida
 * da aplicação também ({@link RecuperacaoDosResumos}).
 */
@Component
public class FilaDeResumosIA implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(FilaDeResumosIA.class);

	private final ThreadPoolExecutor executor;
	private final Set<UUID> pendentes = ConcurrentHashMap.newKeySet();

	public FilaDeResumosIA(IAProperties iaProperties) {
		AtomicInteger numero = new AtomicInteger();
		this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(iaProperties.filaMaximaResumos()),
				tarefa -> {
					Thread thread = new Thread(tarefa, "resumo-ia-" + numero.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				},
				new ThreadPoolExecutor.AbortPolicy());
	}

	/** Põe na fila a geração do resumo. Lança {@link RejectedExecutionException} se a fila estiver cheia. */
	public void enfileirar(UUID resumoId, Runnable tarefa) {
		pendentes.add(resumoId);
		try {
			executor.execute(() -> {
				try {
					rodar(tarefa);
				} finally {
					pendentes.remove(resumoId);
				}
			});
		} catch (RejectedExecutionException filaCheia) {
			pendentes.remove(resumoId);
			throw filaCheia;
		}
	}

	/** Põe na fila uma tarefa que não é um resumo, como o aquecimento do modelo. */
	public void enfileirar(Runnable tarefa) {
		executor.execute(() -> rodar(tarefa));
	}

	/** Se o resumo está na fila ou em geração agora. */
	public boolean pendente(UUID resumoId) {
		return pendentes.contains(resumoId);
	}

	/** A falha fica no log da aplicação, só com o tipo: a mensagem poderia trazer dado clínico. */
	private static void rodar(Runnable tarefa) {
		try {
			tarefa.run();
		} catch (RuntimeException falha) {
			log.error("Tarefa da fila da IA terminou com falha: {}", falha.getClass().getSimpleName());
		}
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}
}
