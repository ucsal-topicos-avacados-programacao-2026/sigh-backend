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

@Component
@RequiredArgsConstructor
public class SugestaoAutomatica {

	private static final Logger LOG = LoggerFactory.getLogger(SugestaoAutomatica.class);

	private final ContextoTriagemService contextoTriagemService;
	private final ClienteIA clienteIA;
	private final TriagemService triagemService;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
		Thread fio = new Thread(tarefa, "sugestao-automatica");
		fio.setDaemon(true);
		return fio;
	});

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoSolicitar(SugestaoSolicitada pedido) {
		executor.execute(() -> gerar(pedido));
	}

	void gerar(SugestaoSolicitada pedido) {
		try {
			CasoTriagemDTO caso = contextoTriagemService.montarCasoAutomatico(pedido.triagemId(), pedido.login(),
					pedido.perfil(), pedido.finalidade());
			CorRisco cor = clienteIA.classificarRisco(caso.descricao());
			triagemService.registrarSugestao(caso.triagemId(), cor, caso.registrosUsados());
		} catch (RuntimeException e) {
			LOG.warn("Sugestão automática indisponível para a triagem {}: {}", pedido.triagemId(), e.getClass().getSimpleName());
			triagemService.marcarSugestaoIndisponivel(pedido.triagemId());
		}
	}

	@PreDestroy
	void encerrar() {
		executor.shutdown();
	}
}
