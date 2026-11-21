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

	public void enfileirar(Runnable tarefa) {
		executor.execute(() -> rodar(tarefa));
	}

	public boolean pendente(UUID resumoId) {
		return pendentes.contains(resumoId);
	}

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
