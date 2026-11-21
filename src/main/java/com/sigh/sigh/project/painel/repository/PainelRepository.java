package com.sigh.sigh.project.painel.repository;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.painel.dto.ChegadaNaFila;
import com.sigh.sigh.project.painel.dto.ContagemComChegada;
import com.sigh.sigh.project.painel.dto.LeitosDoPainelDTO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de contagem do painel (FE-09). Nenhuma devolve dado de paciente: só quantidades, cores e horários de
 * chegada. As regras de "aguardando triagem" e "na fila" são as mesmas do TriagemRepository.
 */
public interface PainelRepository extends Repository<Instituicao, UUID> {

	@Query("""
			select new com.sigh.sigh.project.painel.dto.LeitosDoPainelDTO(l.categoria, l.status, count(l))
			from Leito l
			where l.instituicao.id = :instituicaoId
			group by l.categoria, l.status
			""")
	List<LeitosDoPainelDTO> leitos(UUID instituicaoId);

	/** Em aberto ou em triagem, sem classificação confirmada (a lista "Aguardando triagem" do enfermeiro). */
	@Query("""
			select new com.sigh.sigh.project.painel.dto.ContagemComChegada(count(a), min(a.chegadaEm))
			from Atendimento a
			where a.instituicao.id = :instituicaoId
			  and a.status in (com.sigh.sigh.project.prontuario.entity.StatusAtendimento.ABERTO,
			                   com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM)
			  and not exists (select 1 from Triagem t where t.atendimento = a and t.corConfirmada is not null)
			""")
	ContagemComChegada aguardandoTriagem(UUID instituicaoId);

	/** Classificados à espera do médico, com a cor confirmada mais recente (a fila do enfermeiro). */
	@Query("""
			select new com.sigh.sigh.project.painel.dto.ChegadaNaFila(t.corConfirmada, a.chegadaEm)
			from Triagem t
			join t.atendimento a
			where a.instituicao.id = :instituicaoId
			  and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_TRIAGEM
			  and t.corConfirmada is not null
			  and not exists (
			      select 1 from Triagem posterior
			      where posterior.atendimento = a and posterior.confirmadaEm > t.confirmadaEm)
			""")
	List<ChegadaNaFila> naFila(UUID instituicaoId);

	@Query("""
			select count(a) from Atendimento a
			where a.instituicao.id = :instituicaoId
			  and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_ATENDIMENTO
			""")
	long emAtendimento(UUID instituicaoId);

	/** Internações em curso num leito da instituição (onde o paciente está agora). */
	@Query("""
			select count(o) from OcupacaoLeito o
			where o.leito.instituicao.id = :instituicaoId and o.fimEm is null and o.internacao.altaEm is null
			""")
	long internados(UUID instituicaoId);

	@Query("""
			select count(a) from Atendimento a
			where a.instituicao.id = :instituicaoId and a.chegadaEm >= :desde
			""")
	long chegadasDesde(UUID instituicaoId, LocalDateTime desde);

	@Query("""
			select count(s) from SolicitacaoTransferencia s
			where s.instituicaoDestino.id = :instituicaoId
			  and s.status = com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia.PENDENTE
			""")
	long transferenciasRecebidasPendentes(UUID instituicaoId);

	@Query("""
			select count(s) from SolicitacaoTransferencia s
			where s.instituicaoOrigem.id = :instituicaoId
			  and s.status = com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia.PENDENTE
			""")
	long transferenciasEnviadasPendentes(UUID instituicaoId);
}
