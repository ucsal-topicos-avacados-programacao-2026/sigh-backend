package com.sigh.sigh.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SighProjectApplicationTests {

	@Autowired
	Clock clock;

	@Test
	void contextLoads() {
	}

	@Test
	void relogioUsaOFusoConfiguradoENaoODoServidor() {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Bahia"));
	}

}
