package com.sigh.sigh.project.auditoria.repository;

import com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO;
import com.sigh.sigh.project.auditoria.dto.IndicadorInstituicaoDTO;
import com.sigh.sigh.project.auditoria.dto.RegistroAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, UUID> {

	@Query(value = """
			select new com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO(
				r.ocorridoEm, ator.nome, r.perfil, instituicao.nome, r.finalidadeDeclarada, r.emergencia)
			from RegistroAuditoria r
			join r.ator ator
			left join r.instituicao instituicao
			where r.prontuario.paciente.conta.login = :login
			order by r.ocorridoEm desc
			""", countQuery = """
			select count(r) from RegistroAuditoria r
			where r.prontuario.paciente.conta.login = :login
			""")
	Page<AcessoDoPacienteDTO> buscarAcessosDoPaciente(String login, Pageable paginacao);

	@Query(value = """
			select new com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO(
				r.ocorridoEm, ator.nome, r.perfil, instituicao.nome, r.finalidadeDeclarada, r.emergencia)
			from RegistroAuditoria r
			join r.ator ator
			left join r.instituicao instituicao
			where r.prontuario.paciente.id = :pacienteId
			order by r.ocorridoEm desc
			""", countQuery = """
			select count(r) from RegistroAuditoria r
			where r.prontuario.paciente.id = :pacienteId
			""")
	Page<AcessoDoPacienteDTO> buscarAcessosAoProntuario(UUID pacienteId, Pageable paginacao);

	/**
	 * Registros do período para o auditor. A justificativa é cifrada com o vínculo à linha e só é decifrada
	 * quando a entidade termina de carregar (@PostLoad), então a consulta devolve as entidades e o DTO é
	 * montado depois, nunca dentro do select.
	 */
	default Page<RegistroAuditoriaResponseDTO> buscarRegistros(UUID instituicaoId, Boolean emergencia,
			LocalDateTime inicio, LocalDateTime fim, Pageable paginacao) {
		return buscarRegistrosDoPeriodo(instituicaoId, emergencia, inicio, fim, paginacao)
				.map(RegistroAuditoriaResponseDTO::de);
	}

	@Query(value = """
			select r from RegistroAuditoria r
			join fetch r.ator ator
			left join fetch r.instituicao instituicao
			where r.ocorridoEm >= :inicio and r.ocorridoEm < :fim
			and (:instituicaoId is null or instituicao.id = :instituicaoId)
			and (:emergencia is null or r.emergencia = :emergencia)
			order by r.ocorridoEm desc
			""", countQuery = """
			select count(r) from RegistroAuditoria r
			left join r.instituicao instituicao
			where r.ocorridoEm >= :inicio and r.ocorridoEm < :fim
			and (:instituicaoId is null or instituicao.id = :instituicaoId)
			and (:emergencia is null or r.emergencia = :emergencia)
			""")
	Page<RegistroAuditoria> buscarRegistrosDoPeriodo(UUID instituicaoId, Boolean emergencia,
			LocalDateTime inicio, LocalDateTime fim, Pageable paginacao);

	@Query("""
			select new com.sigh.sigh.project.auditoria.dto.IndicadorInstituicaoDTO(
				instituicao.id, instituicao.nome, count(r),
				sum(case when r.emergencia = true then 1 else 0 end))
			from RegistroAuditoria r
			join r.instituicao instituicao
			where r.ocorridoEm >= :inicio and r.ocorridoEm < :fim
			group by instituicao.id, instituicao.nome
			order by count(r) desc
			""")
	List<IndicadorInstituicaoDTO> contarAcessosPorInstituicao(LocalDateTime inicio, LocalDateTime fim);
}
