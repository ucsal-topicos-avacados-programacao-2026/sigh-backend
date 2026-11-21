package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.ia.service.ClienteIA;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pede a cor sugerida à IA em segundo plano, depois que a admissão (ou os sinais vitais) foi gravada. A IA
 * atende um pedido por vez; a admissão nunca espera por ela. Se a IA falhar, a triagem fica sem sugestão e o
 * enfermeiro classifica à mão, como sempre pôde.
 */
@Component
@RequiredArgsConstructor
public class SugestaoAutomatica {

	private static final Logger LOG = LoggerFactory.getLogger(SugestaoAutomatica.class);

	private final ContextoTriagemService contextoTriagemService;
	private final ClienteIA clienteIA;
	private final TriagemService triagemService;

	// ponytail: fila só em memória; um pedido que estava na fila ao reiniciar fica "em andamento" até o enfermeiro
	// usar "gerar sugestão". Persistir a fila se isso incomodar.
	private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
		Thread fio = new Thread(tarefa, "sugestao-automatica");
		fio.setDaemon(true);
		return fio;
	});

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoSolicitar(SugestaoSolicitada pedido) {
		executor.execute(() -> gerar(pedido));
	}

	/** Visível ao pacote para os testes chamarem sem a fila. */
	void gerar(SugestaoSolicitada pedido) {
		try {
			CasoTriagemDTO caso = contextoTriagemService.montarCasoAutomatico(pedido.triagemId(), pedido.login(),
					pedido.perfil(), pedido.finalidade());
			CorRisco cor = clienteIA.classificarRisco(caso.descricao());
			triagemService.registrarSugestao(caso.triagemId(), cor, caso.registrosUsados());
		} catch (RuntimeException e) {
			// Só o tipo do erro vai ao log: a mensagem pode trazer trecho do caso clínico.
			LOG.warn("Sugestão automática indisponível para a triagem {}: {}", pedido.triagemId(), e.getClass().getSimpleName());
			triagemService.marcarSugestaoIndisponivel(pedido.triagemId());
		}
	}

	@PreDestroy
	void encerrar() {
		executor.shutdown();
	}
}
