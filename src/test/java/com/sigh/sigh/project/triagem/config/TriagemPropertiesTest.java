package com.sigh.sigh.project.triagem.config;

import com.sigh.sigh.project.triagem.entity.CorRisco;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriagemPropertiesTest {

	@Test
	void sistemaNaoSobeSemOTempoMaximoDeTodasAsCores() {
		Map<CorRisco, Duration> semAzul = Map.of(
				CorRisco.VERMELHO, Duration.ZERO,
				CorRisco.LARANJA, Duration.ofMinutes(10),
				CorRisco.AMARELO, Duration.ofMinutes(60),
				CorRisco.VERDE, Duration.ofMinutes(120));

		assertThatThrownBy(() -> new TriagemProperties(semAzul))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AZUL");
	}
}
