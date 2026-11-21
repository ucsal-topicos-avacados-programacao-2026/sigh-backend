package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

	Optional<Paciente> findByCpf(String cpf);

	Optional<Paciente> findByContaLogin(String login);
}
