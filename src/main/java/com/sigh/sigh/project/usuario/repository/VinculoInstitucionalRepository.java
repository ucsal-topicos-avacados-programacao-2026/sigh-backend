package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface VinculoInstitucionalRepository extends JpaRepository<VinculoInstitucional, UUID> {

	/** Verifica se o usuário tem vínculo ativo com a instituição e o perfil habilitado nela. */
	@Query("""
			select count(v) > 0 from VinculoInstitucional v join v.perfisHabilitados p
			where v.usuario.login = :login
			  and v.instituicao.id = :instituicaoId
			  and p = :perfil
			  and v.inicioEm <= :agora
			  and (v.fimEm is null or v.fimEm > :agora)
			""")
	boolean existeVinculoAtivo(@Param("login") String login,
			@Param("instituicaoId") UUID instituicaoId,
			@Param("perfil") Perfil perfil,
			@Param("agora") LocalDateTime agora);
}
