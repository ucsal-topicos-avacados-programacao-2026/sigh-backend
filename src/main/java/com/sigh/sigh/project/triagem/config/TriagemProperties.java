package com.sigh.sigh.project.triagem.config;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("sigh.triagem")
public record TriagemProperties(Map<CorRisco, Duration> tempoMaximoEspera) {

	public Duration tempoMaximoDe(CorRisco cor) {
		return tempoMaximoEspera.get(cor);
	}
}
