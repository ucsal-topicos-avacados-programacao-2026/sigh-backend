package com.sigh.sigh.project.triagem.repository;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.triagem.entity.Triagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriagemRepository extends JpaRepository<Triagem, UUID> {

	/** A classificação de risco confirmada mais recente do atendimento (empate no horário: decide o id). */
	Optional<Triagem> findFirstByAtendimentoIdAndCorConfirmadaIsNotNullOrderByConfirmadaEmDescIdDesc(UUID atendimentoId);

	@Query(value = """
			select t from Triagem t
			join fetch t.atendimento a
			join fetch a.prontuario p
			join fetch p.paciente
			where a.instituicao.id = :instituicaoId
			and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM
			and t.corConfirmada is not null
			and not exists (
				select 1 from Triagem posterior
				where posterior.atendimento = a and posterior.confirmadaEm > t.confirmadaEm)
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
			and not exists (
				select 1 from Triagem posterior
				where posterior.atendimento = a and posterior.confirmadaEm > t.confirmadaEm)
			""")
	Page<Triagem> buscarFila(UUID instituicaoId, Pageable paginacao);

	/**
	 * Atendimentos da instituição em aberto ou em triagem sem classificação confirmada (FE-06), do mais antigo
	 * para o mais recente. Os que já têm classificação confirmada estão na fila (buscarFila).
	 */
	@Query("""
			select a from Atendimento a
			join fetch a.prontuario p
			join fetch p.paciente
			where a.instituicao.id = :instituicaoId
			  and a.status in (com.sigh.sigh.project.prontuario.entity.StatusAtendimento.ABERTO,
			                   com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM)
			  and not exists (select 1 from Triagem t where t.atendimento = a and t.corConfirmada is not null)
			order by a.chegadaEm, a.id
			""")
	List<Atendimento> aguardandoTriagem(UUID instituicaoId);

	/** Triagens do atendimento registradas e ainda sem confirmação. */
	List<Triagem> findByAtendimentoIdAndCorConfirmadaIsNull(UUID atendimentoId);
}
