package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.dto.ReferenciasDaSolicitacao;
import com.sigh.sigh.project.leito.entity.SolicitacaoTransferencia;
import com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitacaoTransferenciaRepository extends JpaRepository<SolicitacaoTransferencia, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SolicitacaoTransferencia> findComBloqueioById(UUID id);

	@Query("""
			select new com.sigh.sigh.project.leito.dto.ReferenciasDaSolicitacao(
				s.internacao.id, s.instituicaoOrigem.id, s.instituicaoDestino.id)
			from SolicitacaoTransferencia s where s.id = :id
			""")
	Optional<ReferenciasDaSolicitacao> buscarReferencias(UUID id);

	List<SolicitacaoTransferencia> findByInternacaoIdAndStatus(UUID internacaoId, StatusSolicitacaoTransferencia status);

	@Query(value = """
			select s from SolicitacaoTransferencia s
			join fetch s.instituicaoOrigem
			join fetch s.instituicaoDestino
			join fetch s.internacao i
			join fetch i.prontuario p
			join fetch p.paciente
			where s.instituicaoOrigem.id = :instituicaoId
			order by s.solicitadaEm desc, s.id
			""", countQuery = """
			select count(s) from SolicitacaoTransferencia s where s.instituicaoOrigem.id = :instituicaoId
			""")
	Page<SolicitacaoTransferencia> buscarEnviadasPelaOrigem(UUID instituicaoId, Pageable paginacao);

	@Query(value = """
			select s from SolicitacaoTransferencia s
			join fetch s.instituicaoOrigem
			join fetch s.internacao i
			join fetch i.prontuario p
			join fetch p.paciente
			where s.instituicaoDestino.id = :instituicaoId
			and s.status = com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia.PENDENTE
			order by s.solicitadaEm, s.id
			""", countQuery = """
			select count(s) from SolicitacaoTransferencia s
			where s.instituicaoDestino.id = :instituicaoId
			and s.status = com.sigh.sigh.project.leito.entity.StatusSolicitacaoTransferencia.PENDENTE
			""")
	Page<SolicitacaoTransferencia> buscarPendentesParaODestino(UUID instituicaoId, Pageable paginacao);
}
