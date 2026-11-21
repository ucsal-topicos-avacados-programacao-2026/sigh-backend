package com.sigh.sigh.project.comum.cripto;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("cifragem")
public class SaudeDaCifragem implements HealthIndicator {

	private final ManutencaoDaCifragem manutencao;
	private final JdbcTemplate jdbc;

	public SaudeDaCifragem(ManutencaoDaCifragem manutencao, JdbcTemplate jdbc) {
		this.manutencao = manutencao;
		this.jdbc = jdbc;
	}

	@Override
	public Health health() {
		Map<String, Integer> fora = manutencao.foraDoFormatoPorColuna();
		List<String> semVacuum = jdbc.queryForList("select tabela from cifragem_vacuum_pendente order by tabela", String.class);
		if (fora.isEmpty() && semVacuum.isEmpty()) {
			return Health.up().build();
		}
		Health.Builder saude = Health.down();
		if (!fora.isEmpty()) {
			saude.withDetail("valoresSemACifraAtual", fora);
		}
		if (!semVacuum.isEmpty()) {
			saude.withDetail("tabelasEsperandoVacuumFull", semVacuum);
		}
		return saude.build();
	}
}
