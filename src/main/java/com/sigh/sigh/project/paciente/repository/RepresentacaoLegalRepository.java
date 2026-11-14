package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepresentacaoLegalRepository extends JpaRepository<RepresentacaoLegal, UUID> {
}
