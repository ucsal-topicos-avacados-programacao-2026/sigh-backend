package com.sigh.sigh.project.usuario;

import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * BE-28 (RF-012): em toda rota, um perfil sem permissão recebe 403 mesmo com a requisição incompleta (corpo vazio,
 * sem parâmetros obrigatórios). Antes, a validação rodava antes da checagem de perfil e a resposta era 400, o que
 * revelava o formato da rota. As rotas vêm do próprio Spring: uma rota nova entra na conferência sozinha.
 */
class PerfilAntesDaValidacaoTest extends TesteIntegracao {

	private static final Pattern PERFIL = Pattern.compile("'([A-Z_]+)'");
	private static final Pattern VARIAVEL = Pattern.compile("\\{[^}]+}");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapeamento;

	private record Caso(HttpMethod metodo, String caminho, String perfilSemPermissao) {
	}

	private List<Caso> casos() {
		List<Caso> casos = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> rota : mapeamento.getHandlerMethods().entrySet()) {
			HandlerMethod metodo = rota.getValue();
			if (!metodo.getBeanType().getPackageName().startsWith("com.sigh")) {
				continue;
			}
			// A mesma busca do interceptor: a regra do método e, sem ela, a da classe.
			PreAuthorize regra = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
			if (regra == null) {
				regra = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
			}
			if (regra == null) {
				continue;
			}
			Set<String> aceitos = PERFIL.matcher(regra.value()).results().map(r -> r.group(1)).collect(Collectors.toSet());
			String caminho = VARIAVEL.matcher(rota.getKey().getPathPatternsCondition().getPatterns().iterator().next()
					.getPatternString()).replaceAll(r -> UUID.randomUUID().toString());
			for (var verbo : rota.getKey().getMethodsCondition().getMethods()) {
				Arrays.stream(Perfil.values()).map(Enum::name).filter(perfil -> !aceitos.contains(perfil))
						.forEach(perfil -> casos.add(new Caso(HttpMethod.valueOf(verbo.name()), caminho, perfil)));
			}
		}
		return casos;
	}

	@Test
	void haRotasParaConferir() {
		List<Caso> casos = casos();
		assertThat(casos).as("rotas com @PreAuthorize e algum perfil de fora").hasSizeGreaterThan(150);
		// Inclui rotas com a regra só na classe (ex.: DadosDoPacienteController, InternacaoController).
		assertThat(casos).extracting(Caso::caminho).anyMatch(c -> c.startsWith("/internacoes"))
				.anyMatch(c -> c.matches("/api/pacientes/[^/]+/restricoes"));
	}

	@TestFactory
	Stream<DynamicTest> perfilSemPermissaoRecebe403MesmoComRequisicaoIncompleta() {
		return casos().stream().map(caso -> dynamicTest(caso.metodo() + " " + caso.caminho() + " como " + caso.perfilSemPermissao(),
				() -> {
					MockHttpServletRequestBuilder requisicao = request(caso.metodo(), caso.caminho())
							.with(user("perfil-sem-permissao").roles(caso.perfilSemPermissao())).with(csrf());
					if (caso.metodo() == HttpMethod.POST || caso.metodo() == HttpMethod.PATCH || caso.metodo() == HttpMethod.PUT) {
						requisicao.contentType(MediaType.APPLICATION_JSON).content("{}");
					}
					int status = mvc.perform(requisicao).andReturn().getResponse().getStatus();
					assertThat(status).as("perfil sem permissão, requisição incompleta").isEqualTo(403);
				}));
	}
}
