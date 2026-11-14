package com.sigh.sigh.project.auditoria.repository;

import com.sigh.sigh.project.auditoria.entity.AnaliseAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnaliseAuditoriaRepository extends JpaRepository<AnaliseAuditoria, UUID> {
}
