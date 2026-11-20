package com.sigh.sigh.project.usuario;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.support.TesteIntegracao;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-02: o front-end sabe quem está logado e em que instituições ele pode operar, com que perfis. */
class UsuarioLogadoTest extends TesteIntegracao {

	@Autowired
	UsuarioRepository usuarioRepository;

	@Autowired
	VinculoInstitucionalRepository vinculoRepository;

	@Autowired
	InstituicaoRepository instituicaoRepository;

	@Test
	void profissionalVeOsVinculosAtivosEmOrdemAlfabeticaComOsPerfisQueEleTem() throws Exception {
		// Criadas fora da ordem; "Ébano" testa a ordem do português (comparando bytes, viria depois de "Zona").
		Instituicao zona = instituicao("Hospital Zona");
		Instituicao ebano = instituicao("Hospital Ébano");
		Instituicao ana = instituicao("Hospital Ana");
		// Seis instituições na resposta: sem a ordenação, a chance de a ordem sair certa por acaso é de 1 em 720.
		Instituicao delta = instituicao("Hospital Delta");
		Instituicao bravo = instituicao("Hospital Bravo");
		Instituicao carmo = instituicao("Hospital Carmo");
		Instituicao soEnfermagem = instituicao("Hospital Bem");
		Instituicao encerrado = instituicao("Hospital Encerrado");
		Instituicao futuro = instituicao("Hospital Futuro");
		Instituicao terminaAgora = instituicao("Hospital Termina Agora");
		String login = cenario.profissional(zona, Perfil.MEDICO);
		Usuario medico = usuarioRepository.findByLogin(login).orElseThrow();
		medico.setNome("Dra. Fictícia");
		usuarioRepository.save(medico);
		LocalDateTime agora = LocalDateTime.now(relogio);
		vinculo(medico, ebano, Perfil.MEDICO, agora.minusDays(5), agora.plusDays(5));
		vinculo(medico, delta, Perfil.MEDICO, agora.minusDays(5), null);
		vinculo(medico, bravo, Perfil.MEDICO, agora.minusDays(5), null);
		vinculo(medico, carmo, Perfil.MEDICO, agora.minusDays(5), null);
		// Começa exatamente agora: já vale.
		vinculo(medico, ana, Perfil.MEDICO, agora, null);
		// No Ébano, um segundo vínculo como enfermeiro: o usuário não tem esse perfil, então ele não aparece.
		vinculo(medico, ebano, Perfil.ENFERMEIRO, agora.minusDays(5), null);
		// Só com perfil que o usuário não tem: a instituição nem aparece.
		vinculo(medico, soEnfermagem, Perfil.ENFERMEIRO, agora.minusDays(5), null);
		vinculo(medico, encerrado, Perfil.MEDICO, agora.minusYears(1), agora.minusDays(1));
		vinculo(medico, futuro, Perfil.MEDICO, agora.plusDays(1), null);
		// Termina exatamente agora: já não vale.
		vinculo(medico, terminaAgora, Perfil.MEDICO, agora.minusDays(5), agora);

		mvc.perform(get("/usuarios/eu").with(user(login).roles("MEDICO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.login").value(login))
				.andExpect(jsonPath("$.nome").value("Dra. Fictícia"))
				.andExpect(jsonPath("$.perfis").value(containsInAnyOrder("MEDICO")))
				.andExpect(jsonPath("$.vinculos[*].instituicaoId").value(contains(ana.getId().toString(),
						bravo.getId().toString(), carmo.getId().toString(), delta.getId().toString(),
						ebano.getId().toString(), zona.getId().toString())))
				.andExpect(jsonPath("$.vinculos[0].instituicaoNome").value(ana.getNome()))
				.andExpect(jsonPath("$.vinculos[4].perfis").value(contains("MEDICO")));
	}

	@Test
	void perfisDeVariosVinculosNaMesmaInstituicaoAparecemJuntos() throws Exception {
		Instituicao hospital = instituicao("Hospital Misto");
		String login = cenario.profissional(hospital, Perfil.MEDICO);
		Usuario usuario = usuarioRepository.findByLogin(login).orElseThrow();
		usuario.getPerfis().add(Perfil.ENFERMEIRO);
		usuarioRepository.save(usuario);
		vinculo(usuario, hospital, Perfil.ENFERMEIRO, LocalDateTime.now(relogio).minusDays(5), null);

		mvc.perform(get("/usuarios/eu").with(user(login).roles("MEDICO", "ENFERMEIRO")))
				.andExpect(jsonPath("$.vinculos.length()").value(1))
				.andExpect(jsonPath("$.vinculos[0].perfis").value(containsInAnyOrder("MEDICO", "ENFERMEIRO")));
	}

	@Test
	void pacienteNaoTemVinculo() throws Exception {
		String paciente = cenario.usuario(Perfil.PACIENTE).getLogin();

		mvc.perform(get("/usuarios/eu").with(user(paciente).roles("PACIENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.perfis").value(containsInAnyOrder("PACIENTE")))
				.andExpect(jsonPath("$.vinculos.length()").value(0));
	}

	@Test
	void semLoginNaoHaDado() throws Exception {
		mvc.perform(get("/usuarios/eu")).andExpect(status().isUnauthorized());
	}

	private Instituicao instituicao(String nome) {
		Instituicao instituicao = cenario.instituicao(true);
		instituicao.setNome(nome + " " + instituicao.getId());
		return instituicaoRepository.save(instituicao);
	}

	private void vinculo(Usuario usuario, Instituicao instituicao, Perfil perfil, LocalDateTime inicio, LocalDateTime fim) {
		VinculoInstitucional vinculo = new VinculoInstitucional();
		vinculo.setUsuario(usuario);
		vinculo.setInstituicao(instituicao);
		vinculo.setPerfisHabilitados(new HashSet<>(Set.of(perfil)));
		vinculo.setInicioEm(inicio);
		vinculo.setFimEm(fim);
		vinculoRepository.save(vinculo);
	}
}
