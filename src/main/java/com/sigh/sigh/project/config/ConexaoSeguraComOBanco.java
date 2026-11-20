package com.sigh.sigh.project.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Com {@code sigh.banco.exigir-tls} (ligado no perfil tls), a aplicação só sobe se a conexão com o
 * banco usar TLS e conferir o certificado e o nome do servidor ({@code sslmode=verify-full}), RNF-004.
 * Confere logo que a fonte de dados é criada, antes de o Flyway ou o JPA usarem a conexão: a URL tem
 * de pedir verify-full e a sessão aberta tem de estar cifrada ({@code pg_stat_ssl}).
 */
@Component
public class ConexaoSeguraComOBanco implements BeanPostProcessor {

	private static final Pattern SSLMODE = Pattern.compile("[?&]sslmode=([^&]*)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FABRICA_OU_VERIFICADOR = Pattern.compile("[?&](sslfactory|sslhostnameverifier)=",
			Pattern.CASE_INSENSITIVE);
	private static final Set<String> PARAMETROS_PROIBIDOS = Set.of("sslmode", "sslfactory", "sslhostnameverifier");

	private final boolean exigir;

	public ConexaoSeguraComOBanco(Environment ambiente) {
		this.exigir = ambiente.getProperty("sigh.banco.exigir-tls", Boolean.class, false);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String nome) {
		if (exigir && bean instanceof DataSource fonte) {
			verificar(fonte);
		}
		return bean;
	}

	static void verificar(DataSource fonte) {
		String url = fonte instanceof HikariDataSource hikari ? hikari.getJdbcUrl() : null;
		if (fonte instanceof HikariDataSource hikari && hikari.getDataSourceProperties().stringPropertyNames().stream()
				.anyMatch(nome -> PARAMETROS_PROIBIDOS.contains(nome.toLowerCase(Locale.ROOT)))) {
			throw new IllegalStateException("A conexão com o banco não pode trocar sslmode, sslfactory nem"
					+ " sslhostnameverifier fora da URL.");
		}
		if (url == null || !pedeVerificacaoCompleta(url)) {
			throw new IllegalStateException("A conexão com o banco precisa de TLS com o certificado conferido:"
					+ " use sslmode=verify-full na URL (DB_URL).");
		}
		try (Connection conexao = fonte.getConnection();
				Statement consulta = conexao.createStatement();
				ResultSet sessao = consulta.executeQuery("select ssl from pg_stat_ssl where pid = pg_backend_pid()")) {
			if (!sessao.next() || !sessao.getBoolean(1)) {
				throw new IllegalStateException("A conexão com o banco não está cifrada (TLS).");
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Não foi possível abrir conexão com TLS com o banco.", e);
		}
	}

	/**
	 * A URL tem exatamente um sslmode, e ele é verify-full, e não troca a fábrica de conexões TLS nem a
	 * verificação do nome do servidor (que poderiam desligar a conferência do certificado).
	 */
	static boolean pedeVerificacaoCompleta(String url) {
		if (FABRICA_OU_VERIFICADOR.matcher(url).find()) {
			return false;
		}
		Matcher modo = SSLMODE.matcher(url);
		if (!modo.find() || !modo.group(1).equalsIgnoreCase("verify-full")) {
			return false;
		}
		return !modo.find();
	}
}
