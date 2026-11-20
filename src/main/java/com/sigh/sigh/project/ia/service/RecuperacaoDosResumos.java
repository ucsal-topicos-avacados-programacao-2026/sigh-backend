package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.ia.repository.ResumoHistoricoIARepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Ao subir, antes de o servidor web aceitar pedidos: o resumo que estava na fila da memória quando a
 * aplicação parou não será mais gerado, então vira falho, e o médico vê que precisa pedir de novo.
 * Supõe uma instância só da aplicação, como a fila.
 */
@Component
@RequiredArgsConstructor
public class RecuperacaoDosResumos implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(RecuperacaoDosResumos.class);

	private final ResumoHistoricoIARepository resumoHistoricoIARepository;

	@Override
	public void afterSingletonsInstantiated() {
		int interrompidos = resumoHistoricoIARepository.marcarInterrompidosComoFalhos();
		if (interrompidos > 0) {
			log.warn("{} resumo(s) por IA interrompido(s) quando a aplicação parou foram marcados como falhos.",
					interrompidos);
		}
	}
}
