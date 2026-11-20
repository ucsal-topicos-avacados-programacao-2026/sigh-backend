package com.sigh.sigh.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda data e hora do back-end vem do relógio único (bean {@code Clock}, fuso em
 * {@code sigh.fuso-horario}). Pegar a hora direto do sistema faz o resultado depender do fuso do
 * servidor: com a JVM em UTC, a fila mostrava esperas 3 horas maiores.
 */
class RelogioUnicoTest {

	private static final Path CODIGO_DE_PRODUCAO = Path.of("src/main/java");

	/** Só é permitido {@code now(clock)}; qualquer outra forma usa o relógio ou o fuso do sistema. */
	private static final Pattern HORA_DO_SISTEMA = Pattern.compile(
			"\\b(LocalDateTime|LocalDate|LocalTime|Instant|ZonedDateTime|OffsetDateTime|OffsetTime|Year|YearMonth|MonthDay)"
					+ "\\s*(\\.now\\((?!\\s*clock\\s*\\))|::now\\b)"
					+ "|\\bimport\\s+static\\s+java\\.time\\.\\w+\\.now\\b"
					+ "|\\bClock\\.(systemDefaultZone|systemUTC)\\("
					+ "|\\bZoneId\\.systemDefault\\(|\\bTimeZone\\.getDefault\\(|\\bCalendar\\.getInstance\\("
					+ "|\\bSystem\\.currentTimeMillis\\("
					+ "|\\bnew\\s+(java\\.util\\.)?Date\\(\\s*\\)"
					+ "|\\bnew\\s+SystemTimeProvider\\(");

	@Test
	void codigoDeProducaoNaoPegaAHoraDiretoDoSistema() throws IOException {
		assertThat(CODIGO_DE_PRODUCAO).isDirectory();
		List<String> ocorrencias = new ArrayList<>();
		try (Stream<Path> arquivos = Files.walk(CODIGO_DE_PRODUCAO)) {
			for (Path arquivo : arquivos.filter(caminho -> caminho.toString().endsWith(".java")).toList()) {
				List<String> linhas = Files.readAllLines(arquivo);
				for (int i = 0; i < linhas.size(); i++) {
					if (HORA_DO_SISTEMA.matcher(linhas.get(i)).find()) {
						ocorrencias.add(CODIGO_DE_PRODUCAO.relativize(arquivo) + ":" + (i + 1) + "  " + linhas.get(i).strip());
					}
				}
			}
		}
		assertThat(ocorrencias)
				.as("Use o Clock injetado, por exemplo LocalDateTime.now(clock)")
				.isEmpty();
	}

	@Test
	void verificacaoReconheceAsFormasProibidas() {
		assertThat(HORA_DO_SISTEMA.matcher("LocalDateTime agora = LocalDateTime.now();").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("Instant agora = Instant.now( );").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("new DefaultCodeVerifier(gerador, new SystemTimeProvider())").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("LocalDateTime.now(ZoneOffset.UTC)").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("Supplier<Instant> agora = Instant::now;").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("import static java.time.LocalDateTime.now;").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("ZoneId fuso = ZoneId.systemDefault();").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("Calendar.getInstance()").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("java.time.LocalDate.now()").find()).isTrue();
		assertThat(HORA_DO_SISTEMA.matcher("LocalDateTime agora = LocalDateTime.now(clock);").find()).isFalse();
		assertThat(HORA_DO_SISTEMA.matcher("Clock.system(ZoneId.of(fusoHorario))").find()).isFalse();
	}
}
