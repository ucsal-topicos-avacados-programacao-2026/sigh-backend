package com.sigh.sigh.project.paciente.repository;

import com.sigh.sigh.project.paciente.entity.RestricaoAcesso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RestricaoAcessoRepository extends JpaRepository<RestricaoAcesso, UUID> {

	@Query("select ra from RestricaoAcesso ra join fetch ra.definidaPor where ra.prontuario.id = :prontuarioId order by ra.inicioEm desc")
	List<RestricaoAcesso> doProntuario(@Param("prontuarioId") UUID prontuarioId);

	@Query("""
			select count(ra) > 0 from RestricaoAcesso ra
			where ra.prontuario.id = :prontuarioId and ra.registroAlvo.id = :registroId
			  and ra.inicioEm <= :agora and (ra.fimEm is null or ra.fimEm > :agora)
			""")
	boolean existeVigenteDoRegistro(@Param("prontuarioId") UUID prontuarioId, @Param("registroId") UUID registroId,
			@Param("agora") LocalDateTime agora);

	@Query("""
			select count(ra) > 0 from RestricaoAcesso ra
			where ra.prontuario.id = :prontuarioId and lower(ra.categoriaAlvo) = lower(:categoria)
			  and ra.inicioEm <= :agora and (ra.fimEm is null or ra.fimEm > :agora)
			""")
	boolean existeVigenteDaCategoria(@Param("prontuarioId") UUID prontuarioId, @Param("categoria") String categoria,
			@Param("agora") LocalDateTime agora);
}
