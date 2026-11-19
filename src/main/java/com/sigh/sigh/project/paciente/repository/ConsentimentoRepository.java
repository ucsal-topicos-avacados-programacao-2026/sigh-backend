package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, UUID> {

	Optional<Consentimento> findFirstByPacienteIdAndTipoOrderByVersaoDesc(UUID pacienteId, TipoConsentimento tipo);
}
