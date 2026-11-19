package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.RestricaoAcesso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestricaoAcessoRepository extends JpaRepository<RestricaoAcesso, UUID> {
}
