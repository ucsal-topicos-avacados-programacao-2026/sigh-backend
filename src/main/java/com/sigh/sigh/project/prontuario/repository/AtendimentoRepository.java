package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AtendimentoRepository extends JpaRepository<Atendimento, UUID> {
}
