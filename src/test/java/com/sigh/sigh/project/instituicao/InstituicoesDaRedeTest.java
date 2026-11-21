package com.sigh.sigh.project.instituicao;

import com.jayway.jsonpath.JsonPath;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.support.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FE-05 e FE-04: o paciente escolhe a quem liberar uma informação restrita (RF-009), e o administrativo escolhe o
 * destino de uma transferência (RF-006), entre as instituições credenciadas.
 */
class InstituicoesDaRedeTest extends TesteIntegracao {

	@Autowired
	InstituicaoRepository instituicaoRepository;

	@Test
	void pacienteVeSoAsCredenciadasEmOrdemDoPortuguesComIdENome() throws Exception {
		// Prefixo próprio: o banco dos testes tem instituições de outros casos, que ficam de fora da conferência.
		String grupo = "Rede " + UUID.randomUUID().toString().substring(0, 8);
		Instituicao zona = instituicao(grupo + " Zona", true);
		Instituicao ebano = instituicao(grupo + " Ébano", true);
		Instituicao ana = instituicao(grupo + " Ana", true);
		// Seis com o mesmo nome: sem o desempate pelo id, a ordem do banco só bate por acaso (1 em 720).
		List<Instituicao> iguais = Stream.generate(() -> instituicao(grupo + " Igual", true)).limit(6).toList();
		Instituicao descredenciada = instituicao(grupo + " Bravo", false);

		String corpo = mvc.perform(get("/api/instituicoes").with(user("paciente.qualquer").roles("PACIENTE")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		List<Map<String, Object>> doGrupo = JsonPath.<List<Map<String, Object>>>read(corpo, "$[*]").stream()
				.filter(item -> String.valueOf(item.get("nome")).startsWith(grupo))
				.toList();
		// Mesmo nome: o id desempata, na ordem do UUID.
		List<String> esperado = new ArrayList<>(List.of(ana.getId().toString(), ebano.getId().toString()));
		iguais.stream().map(Instituicao::getId).sorted(Comparator.naturalOrder()).map(UUID::toString).forEach(esperado::add);
		esperado.add(zona.getId().toString());
		assertThat(doGrupo).extracting(item -> item.get("id")).containsExactlyElementsOf(esperado);
		assertThat(doGrupo).extracting(item -> item.get("id")).doesNotContain(descredenciada.getId().toString());
		assertThat(doGrupo.getFirst()).containsOnlyKeys("id", "nome").containsEntry("nome", ana.getNome());
	}

	@Test
	void administrativoTambemListaSemPrecisarDeVinculo() throws Exception {
		Instituicao credenciada = instituicao("Rede " + UUID.randomUUID().toString().substring(0, 8) + " Destino", true);
		String corpo = mvc.perform(get("/api/instituicoes").with(user("administrativo.sem.vinculo").roles("ADMINISTRATIVO")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(corpo, "$[*].id")).contains(credenciada.getId().toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "MEDICO", "ENFERMEIRO", "AUDITOR_GESTOR" })
	void outrosPerfisNaoListam(String perfil) throws Exception {
		mvc.perform(get("/api/instituicoes").with(user("profissional.qualquer").roles(perfil)))
				.andExpect(status().isForbidden());
	}

	@Test
	void semLoginNaoLista() throws Exception {
		mvc.perform(get("/api/instituicoes")).andExpect(status().isUnauthorized());
	}

	private Instituicao instituicao(String nome, boolean credenciada) {
		Instituicao instituicao = cenario.instituicao(credenciada);
		instituicao.setNome(nome);
		return instituicaoRepository.save(instituicao);
	}
}
