package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RegistroClinicoRepository extends JpaRepository<RegistroClinico, UUID> {

	@Query("""
			select r from RegistroClinico r
			join fetch r.origem
			where r.prontuario.id = :prontuarioId
			and not exists (
				select 1 from RestricaoAcesso restricao
				where restricao.prontuario.id = :prontuarioId
				and restricao.inicioEm <= :agora
				and (restricao.fimEm is null or restricao.fimEm > :agora)
				and (restricao.registroAlvo.id = r.id or restricao.categoriaAlvo = r.categoriaInformacao))
			order by r.criadoEm desc
			""")
	List<RegistroClinico> buscarHistoricoSemRestricao(UUID prontuarioId, LocalDateTime agora, Limit limite);
}
