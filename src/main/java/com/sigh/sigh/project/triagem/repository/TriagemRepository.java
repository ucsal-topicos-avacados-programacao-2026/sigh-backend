package com.sigh.sigh.project.triagem.repository;

import com.sigh.sigh.project.triagem.entity.Triagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface TriagemRepository extends JpaRepository<Triagem, UUID> {

	@Query(value = """
			select t from Triagem t
			join fetch t.atendimento a
			join fetch a.prontuario p
			join fetch p.paciente
			where a.instituicao.id = :instituicaoId
			and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM
			and t.corConfirmada is not null
			order by case t.corConfirmada
				when com.sigh.sigh.project.triagem.entity.CorRisco.VERMELHO then 1
				when com.sigh.sigh.project.triagem.entity.CorRisco.LARANJA then 2
				when com.sigh.sigh.project.triagem.entity.CorRisco.AMARELO then 3
				when com.sigh.sigh.project.triagem.entity.CorRisco.VERDE then 4
				else 5
			end, a.chegadaEm
			""", countQuery = """
			select count(t) from Triagem t
			join t.atendimento a
			where a.instituicao.id = :instituicaoId
			and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM
			and t.corConfirmada is not null
			""")
	Page<Triagem> buscarFila(UUID instituicaoId, Pageable paginacao);
}
