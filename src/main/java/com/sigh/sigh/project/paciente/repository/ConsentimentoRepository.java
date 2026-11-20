package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, UUID> {

	@Query("select coalesce(max(c.versao), 0) from Consentimento c where c.paciente.id = :pacienteId")
	int ultimaVersao(@Param("pacienteId") UUID pacienteId);

	/** Decisões sobre o compartilhamento amplo na rede (sem alvo específico), da mais recente para a mais antiga. */
	@Query("""
			select c from Consentimento c
			where c.paciente.id = :pacienteId and c.tipo = :tipo
			  and c.destinataria is null and c.categoriaAlvo is null and c.registroAlvo is null
			order by c.versao desc
			""")
	List<Consentimento> decisoesAmplas(@Param("pacienteId") UUID pacienteId,
			@Param("tipo") TipoConsentimento tipo, Limit limite);

	@Query("select c from Consentimento c where c.paciente.id = :pacienteId order by c.versao asc")
	List<Consentimento> historico(@Param("pacienteId") UUID pacienteId);

	default Optional<Consentimento> decisaoAmplaVigente(UUID pacienteId) {
		return decisoesAmplas(pacienteId, TipoConsentimento.AMPLO, Limit.of(1)).stream().findFirst();
	}

	Optional<Consentimento> findFirstByPacienteIdAndTipoOrderByVersaoDesc(UUID pacienteId, TipoConsentimento tipo);
}
