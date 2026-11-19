package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.Prontuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProntuarioRepository extends JpaRepository<Prontuario, UUID> {

	Optional<Prontuario> findByPacienteId(UUID pacienteId);
}
