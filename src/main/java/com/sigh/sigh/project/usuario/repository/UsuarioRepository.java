package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

	Optional<Usuario> findByLogin(String login);

	boolean existsByLogin(String login);

	boolean existsByLoginAndAtivoTrue(String login);

	@Query("select count(u) from Usuario u where :perfil member of u.perfis and u.ativo = true")
	long contarAtivosComPerfil(@Param("perfil") Perfil perfil);

	@Query(value = """
			select u from Usuario u
			where (:qualquerSituacao = true or u.ativo = :ativo)
			  and (:qualquerPerfil = true or :perfil member of u.perfis)
			  and (lower(u.login) like :busca or lower(u.nome) like :busca)
			order by lower(u.nome), u.id
			""", countQuery = """
			select count(u) from Usuario u
			where (:qualquerSituacao = true or u.ativo = :ativo)
			  and (:qualquerPerfil = true or :perfil member of u.perfis)
			  and (lower(u.login) like :busca or lower(u.nome) like :busca)
			""")
	Page<Usuario> buscar(@Param("qualquerSituacao") boolean qualquerSituacao, @Param("ativo") boolean ativo,
			@Param("qualquerPerfil") boolean qualquerPerfil, @Param("perfil") Perfil perfil,
			@Param("busca") String busca, Pageable paginacao);
}
