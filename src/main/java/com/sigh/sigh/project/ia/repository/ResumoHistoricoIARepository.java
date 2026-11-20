package com.sigh.sigh.project.ia.repository;

import com.sigh.sigh.project.ia.entity.ResumoHistoricoIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ResumoHistoricoIARepository extends JpaRepository<ResumoHistoricoIA, UUID> {

	/** Resumos que estavam na fila da memória quando a aplicação parou: não serão mais gerados. */
	@Modifying
	@Transactional
	@Query("""
			update ResumoHistoricoIA r set r.status = com.sigh.sigh.project.ia.entity.StatusResumo.FALHOU
			where r.status = com.sigh.sigh.project.ia.entity.StatusResumo.SOLICITADO
			""")
	int marcarInterrompidosComoFalhos();
}
