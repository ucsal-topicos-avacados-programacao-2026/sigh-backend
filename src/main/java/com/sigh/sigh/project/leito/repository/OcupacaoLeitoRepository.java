package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.OcupacaoLeito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OcupacaoLeitoRepository extends JpaRepository<OcupacaoLeito, UUID> {

	@Query("""
			select o from OcupacaoLeito o
			join fetch o.leito
			where o.internacao.id = :internacaoId and o.fimEm is null
			""")
	Optional<OcupacaoLeito> buscarOcupacaoAtual(UUID internacaoId);

	/** Instituição onde o paciente está internado agora (a do leito ocupado). */
	@Query("select o.leito.instituicao.id from OcupacaoLeito o where o.internacao.id = :internacaoId and o.fimEm is null")
	Optional<UUID> instituicaoDaOcupacaoAtual(UUID internacaoId);
}
