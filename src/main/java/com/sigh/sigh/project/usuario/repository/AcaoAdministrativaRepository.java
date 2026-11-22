package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.AcaoAdministrativa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AcaoAdministrativaRepository extends JpaRepository<AcaoAdministrativa, UUID> {

	@Query(value = """
			select a from AcaoAdministrativa a
			join fetch a.ator
			join fetch a.alvo
			order by a.ocorridoEm desc, a.id
			""", countQuery = "select count(a) from AcaoAdministrativa a")
	Page<AcaoAdministrativa> recentes(Pageable paginacao);
}
