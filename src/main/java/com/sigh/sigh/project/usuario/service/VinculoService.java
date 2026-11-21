package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VinculoService {

	static final String SEM_VINCULO = "Sem vínculo ativo com a instituição.";

	private final VinculoInstitucionalRepository vinculoRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public void exigirVinculoAtivo(String login, UUID instituicaoId, Perfil... perfis) {
		perfilHabilitado(login, instituicaoId, perfis);
	}

	@Transactional(readOnly = true)
	public Perfil perfilHabilitado(String login, UUID instituicaoId, Perfil... perfis) {
		if (login == null || instituicaoId == null) {
			throw new AccessDeniedException(SEM_VINCULO);
		}
		LocalDateTime agora = LocalDateTime.now(clock);
		for (Perfil perfil : perfis) {
			if (vinculoRepository.existeVinculoAtivo(login, instituicaoId, perfil, agora)) {
				return perfil;
			}
		}
		throw new AccessDeniedException(SEM_VINCULO);
	}
}
