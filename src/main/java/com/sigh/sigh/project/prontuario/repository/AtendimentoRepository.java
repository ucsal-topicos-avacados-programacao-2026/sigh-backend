package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AtendimentoRepository extends JpaRepository<Atendimento, UUID> {

	/** Indica se a instituição já atendeu o paciente até o instante informado (RN-007). */
	boolean existsByProntuarioPacienteIdAndInstituicaoId(UUID pacienteId, UUID instituicaoId);

	boolean existsByProntuarioPacienteIdAndInstituicaoIdAndChegadaEmLessThanEqual(
			UUID pacienteId, UUID instituicaoId, LocalDateTime ate);
}
