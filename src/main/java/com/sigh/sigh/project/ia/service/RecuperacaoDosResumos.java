package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.ia.repository.ResumoHistoricoIARepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

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
