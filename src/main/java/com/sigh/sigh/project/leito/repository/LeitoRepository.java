package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface LeitoRepository extends JpaRepository<Leito, UUID> {

	boolean existsByInstituicaoIdAndCodigo(UUID instituicaoId, String codigo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Leito> findComBloqueioById(UUID id);

	@Query("""
			select l from Leito l
			where (:instituicaoId is null or l.instituicao.id = :instituicaoId)
			and (:categoria is null or l.categoria = :categoria)
			and (:status is null or l.status = :status)
			""")
	Page<Leito> buscar(UUID instituicaoId, CategoriaLeito categoria, StatusLeito status, Pageable paginacao);
}
