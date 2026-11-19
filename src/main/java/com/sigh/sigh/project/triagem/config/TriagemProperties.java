package com.sigh.sigh.project.triagem.config;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("sigh.triagem")
public record TriagemProperties(Map<CorRisco, Duration> tempoMaximoEspera) {

	public TriagemProperties {
		exigirTempoParaTodasAsCores(tempoMaximoEspera);
	}

	public Duration tempoMaximoDe(CorRisco cor) {
		return tempoMaximoEspera.get(cor);
	}

	private static void exigirTempoParaTodasAsCores(Map<CorRisco, Duration> tempos) {
		List<CorRisco> coresSemTempo = Arrays.stream(CorRisco.values())
				.filter(cor -> tempos == null || !tempos.containsKey(cor))
				.toList();
		if (!coresSemTempo.isEmpty()) {
			throw new IllegalStateException("Configure sigh.triagem.tempo-maximo-espera para: " + coresSemTempo);
		}
	}
}
