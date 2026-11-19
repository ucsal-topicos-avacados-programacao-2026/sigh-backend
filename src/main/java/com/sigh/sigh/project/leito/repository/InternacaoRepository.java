package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.Internacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternacaoRepository extends JpaRepository<Internacao, UUID> {

	boolean existsByProntuarioIdAndAltaEmIsNull(UUID prontuarioId);
}
