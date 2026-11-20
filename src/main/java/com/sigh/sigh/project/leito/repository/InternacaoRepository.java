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

	/**
	 * Com bloqueio. Toda operação que mexe no leito de uma internação (transferência, alta, pedido,
	 * aceite, recusa, cancelamento) trava a internação primeiro, sempre na mesma ordem: internação,
	 * solicitação, leito. Assim, a origem e o destino nunca mudam o leito do paciente ao mesmo tempo (BE-21).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Internacao i where i.id = :id")
	Optional<Internacao> findComBloqueioById(UUID id);
}
