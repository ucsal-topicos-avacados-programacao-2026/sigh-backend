package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.usuario.dto.UsuarioLogadoDTO;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final VinculoInstitucionalRepository vinculoRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Usuario buscarPorLogin(String login) {
		return usuarioRepository.findByLogin(login)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
	}

	/** Ordem do seletor do front: nome da instituição com as regras do português, e o id para desempatar. */
	private static final Comparator<UsuarioLogadoDTO.VinculoAtivo> ORDEM_DOS_VINCULOS = Comparator
			.comparing(UsuarioLogadoDTO.VinculoAtivo::instituicaoNome, Collator.getInstance(Locale.of("pt", "BR")))
			.thenComparing(UsuarioLogadoDTO.VinculoAtivo::instituicaoId);

	/**
	 * Quem está logado: nome, perfis e vínculos ativos, com os perfis de cada instituição juntos (dois vínculos
	 * na mesma instituição viram um). Cada vínculo só traz os perfis que o usuário tem: operar exige os dois (o
	 * perfil, no token, e o vínculo que o habilita na instituição).
	 */
	@Transactional(readOnly = true)
	public UsuarioLogadoDTO logado(String login) {
		Usuario usuario = buscarPorLogin(login);
		Map<UUID, String> nomes = new HashMap<>();
		Map<UUID, Set<Perfil>> perfisPorInstituicao = new HashMap<>();
		for (VinculoInstitucional vinculo : vinculoRepository.buscarAtivos(login, LocalDateTime.now(clock))) {
			UUID instituicaoId = vinculo.getInstituicao().getId();
			nomes.put(instituicaoId, vinculo.getInstituicao().getNome());
			Set<Perfil> habilitados = perfisPorInstituicao.computeIfAbsent(instituicaoId, id -> EnumSet.noneOf(Perfil.class));
			vinculo.getPerfisHabilitados().stream().filter(usuario.getPerfis()::contains).forEach(habilitados::add);
		}
		List<UsuarioLogadoDTO.VinculoAtivo> vinculos = perfisPorInstituicao.entrySet().stream()
				.filter(entrada -> !entrada.getValue().isEmpty())
				.map(entrada -> new UsuarioLogadoDTO.VinculoAtivo(entrada.getKey(), nomes.get(entrada.getKey()),
						entrada.getValue()))
				.sorted(ORDEM_DOS_VINCULOS)
				.toList();
		Set<Perfil> perfis = EnumSet.noneOf(Perfil.class);
		perfis.addAll(usuario.getPerfis());
		return new UsuarioLogadoDTO(usuario.getLogin(), usuario.getNome(), perfis, vinculos);
	}
}
