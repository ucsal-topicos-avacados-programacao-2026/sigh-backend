package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.Consentimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, UUID> {
}
