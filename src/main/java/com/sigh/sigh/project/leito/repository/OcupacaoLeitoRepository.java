package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.OcupacaoLeito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OcupacaoLeitoRepository extends JpaRepository<OcupacaoLeito, UUID> {

	@Query("""
			select o from OcupacaoLeito o
			join fetch o.leito
			where o.internacao.id = :internacaoId and o.fimEm is null
			""")
	Optional<OcupacaoLeito> buscarOcupacaoAtual(UUID internacaoId);

	@Query("select o.leito.instituicao.id from OcupacaoLeito o where o.internacao.id = :internacaoId and o.fimEm is null")
	Optional<UUID> instituicaoDaOcupacaoAtual(UUID internacaoId);

	@Query("""
			select o from OcupacaoLeito o
			join fetch o.leito l
			join fetch o.internacao i
			join fetch i.prontuario p
			join fetch p.paciente
			join fetch i.atendimentoOrigem
			where l.instituicao.id = :instituicaoId and o.fimEm is null and i.altaEm is null
			order by l.codigo, l.id
			""")
	List<OcupacaoLeito> atuaisDaInstituicao(UUID instituicaoId);
}
