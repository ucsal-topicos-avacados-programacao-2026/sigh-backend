package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VinculoInstitucionalRepository extends JpaRepository<VinculoInstitucional, UUID> {

	@Query("""
			select distinct v from VinculoInstitucional v
			join fetch v.instituicao
			left join fetch v.perfisHabilitados
			where v.usuario.login = :login
			  and v.inicioEm <= :agora
			  and (v.fimEm is null or v.fimEm > :agora)
			""")
	List<VinculoInstitucional> buscarAtivos(@Param("login") String login, @Param("agora") LocalDateTime agora);

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

	@Query("""
			select distinct v from VinculoInstitucional v
			join fetch v.instituicao
			left join fetch v.perfisHabilitados
			where v.usuario.id in :ids
			order by v.inicioEm, v.id
			""")
	List<VinculoInstitucional> buscarDosUsuarios(@Param("ids") Collection<UUID> ids);

	@Query("""
			select count(v) > 0 from VinculoInstitucional v
			where v.usuario.id = :usuarioId
			  and v.instituicao.id = :instituicaoId
			  and (v.fimEm is null or v.fimEm > :agora)
			""")
	boolean existeAtivoDoUsuarioNaInstituicao(@Param("usuarioId") UUID usuarioId,
			@Param("instituicaoId") UUID instituicaoId, @Param("agora") LocalDateTime agora);
}
