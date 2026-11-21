package com.sigh.sigh.project.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BE-28 (RF-012): confere o perfil da rota antes de o Spring ler e validar a requisição. O @PreAuthorize roda
 * depois da leitura do corpo e dos parâmetros, então um perfil sem permissão que mandava uma requisição incompleta
 * recebia 400 (requisição inválida) e não 403, e a resposta revelava o formato da rota. Aqui a mesma regra do
 * @PreAuthorize é aplicada antes; o @PreAuthorize continua valendo como segunda checagem.
 *
 * <p>Só lê as regras {@code hasRole('X')} e {@code hasAnyRole('X', 'Y')}, as únicas que a aplicação usa (o
 * VinculoEmTodaRotaTest garante isso). Qualquer outra fica só com o @PreAuthorize.
 */
public class PerfilAntesDaRequisicao implements HandlerInterceptor {

	private static final Pattern REGRA = Pattern.compile("has(Any)?Role\\('[A-Z_]+'(\\s*,\\s*'[A-Z_]+')*\\)");
	private static final Pattern PERFIL = Pattern.compile("'([A-Z_]+)'");

	@Override
	public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object rota) {
		if (!(rota instanceof HandlerMethod metodo)) {
			return true;
		}
		PreAuthorize regra = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
		if (regra == null) {
			regra = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
		}
		if (regra == null || !REGRA.matcher(regra.value().strip()).matches()) {
			return true;
		}
		Authentication usuario = SecurityContextHolder.getContext().getAuthentication();
		if (usuario == null || !usuario.isAuthenticated()) {
			// Sem login, quem responde é a cadeia de segurança (401).
			return true;
		}
		Set<String> aceitos = perfis(regra.value());
		boolean temPerfil = usuario.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.anyMatch(autoridade -> aceitos.contains(autoridade));
		if (!temPerfil) {
			throw new AccessDeniedException("Perfil sem permissão para esta operação.");
		}
		return true;
	}

	private static Set<String> perfis(String regra) {
		Matcher achado = PERFIL.matcher(regra);
		return achado.results().map(r -> "ROLE_" + r.group(1)).collect(Collectors.toUnmodifiableSet());
	}
}
