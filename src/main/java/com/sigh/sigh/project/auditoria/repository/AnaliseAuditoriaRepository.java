package com.sigh.sigh.project.auditoria.repository;

import com.sigh.sigh.project.auditoria.entity.AnaliseAuditoria;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnaliseAuditoriaRepository extends JpaRepository<AnaliseAuditoria, UUID> {

	@EntityGraph(attributePaths = "registroAuditoria")
	Page<AnaliseAuditoria> findByTipoOrderByRegistradaEmDesc(TipoAnalise tipo, Pageable paginacao);
}
