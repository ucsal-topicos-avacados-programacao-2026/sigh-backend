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
}
