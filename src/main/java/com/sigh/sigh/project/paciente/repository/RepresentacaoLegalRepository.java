package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RepresentacaoLegalRepository extends JpaRepository<RepresentacaoLegal, UUID> {

	@Query("""
			select count(r) > 0 from RepresentacaoLegal r
			where r.paciente.id = :pacienteId
			  and r.representante.login = :login
			  and r.inicioEm <= :agora
			  and (r.fimEm is null or r.fimEm > :agora)
			""")
	boolean existeVigente(@Param("pacienteId") UUID pacienteId, @Param("login") String login,
			@Param("agora") LocalDateTime agora);

	@Query("""
			select r.paciente from RepresentacaoLegal r
			where r.representante.login = :login
			  and r.inicioEm <= :agora
			  and (r.fimEm is null or r.fimEm > :agora)
			""")
	List<Paciente> representadosVigentes(@Param("login") String login, @Param("agora") LocalDateTime agora);

	@Query("select r from RepresentacaoLegal r join fetch r.representante where r.paciente.id = :pacienteId order by r.inicioEm desc")
	List<RepresentacaoLegal> doPaciente(@Param("pacienteId") UUID pacienteId);
}
