package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.Internacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InternacaoRepository extends JpaRepository<Internacao, UUID> {

	boolean existsByProntuarioIdAndAltaEmIsNull(UUID prontuarioId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Internacao i where i.id = :id")
	Optional<Internacao> findComBloqueioById(UUID id);
}
