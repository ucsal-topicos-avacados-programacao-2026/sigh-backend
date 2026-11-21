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
