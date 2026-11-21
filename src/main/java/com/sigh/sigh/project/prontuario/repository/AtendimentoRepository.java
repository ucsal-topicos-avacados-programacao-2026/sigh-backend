package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AtendimentoRepository extends JpaRepository<Atendimento, UUID> {

	/** Indica se a instituição já atendeu o paciente até o instante informado (RN-007). */
	boolean existsByProntuarioPacienteIdAndInstituicaoId(UUID pacienteId, UUID instituicaoId);

	boolean existsByProntuarioPacienteIdAndInstituicaoIdAndChegadaEmLessThanEqual(
			UUID pacienteId, UUID instituicaoId, LocalDateTime ate);

	/**
	 * Atendimentos em aberto da instituição cujo paciente não está internado: de onde o administrativo escolhe
	 * quem internar, e a fila de espera quando não há leito livre (FE-04). Do mais antigo para o mais recente.
	 */
	@Query("""
			select a from Atendimento a
			join fetch a.prontuario p
			join fetch p.paciente
			where a.instituicao.id = :instituicaoId
			  and a.status <> com.sigh.sigh.project.prontuario.entity.StatusAtendimento.ENCERRADO
			  and not exists (select 1 from Internacao i where i.prontuario = p and i.altaEm is null)
			order by a.chegadaEm, a.id
			""")
	List<Atendimento> abertosSemInternacao(UUID instituicaoId);

	/** Atendimentos em andamento com o médico na instituição, do que começou antes para o mais recente (FE-07). */
	@Query("""
			select a from Atendimento a
			join fetch a.prontuario p
			join fetch p.paciente
			left join fetch a.medicoResponsavel
			where a.instituicao.id = :instituicaoId
			  and a.status = com.sigh.sigh.project.prontuario.entity.StatusAtendimento.EM_ATENDIMENTO
			order by a.inicioClinicoEm, a.id
			""")
	List<Atendimento> emAndamento(UUID instituicaoId);
}
