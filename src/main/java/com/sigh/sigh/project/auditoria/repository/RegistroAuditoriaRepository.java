package com.sigh.sigh.project.auditoria.repository;

import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, UUID> {
}
