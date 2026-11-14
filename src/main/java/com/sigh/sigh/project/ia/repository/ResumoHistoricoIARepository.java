package com.sigh.sigh.project.ia.repository;

import com.sigh.sigh.project.ia.entity.ResumoHistoricoIA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResumoHistoricoIARepository extends JpaRepository<ResumoHistoricoIA, UUID> {
}
