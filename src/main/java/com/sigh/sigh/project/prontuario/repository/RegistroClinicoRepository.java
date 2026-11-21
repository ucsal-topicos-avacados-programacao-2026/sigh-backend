package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RegistroClinicoRepository extends JpaRepository<RegistroClinico, UUID> {

	String ACENTOS = "ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç";
	String SEM_ACENTOS = "AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc";
	String NORMALIZAR = "lower(function('translate', trim(function('regexp_replace', ";
	String FIM_NORMALIZAR = ", '\\s+', ' ', 'g')), '" + ACENTOS + "', '" + SEM_ACENTOS + "'))";

	String RESTRICAO_VIGENTE = """
			exists (select 1 from RestricaoAcesso ra
				where ra.prontuario.id = r.prontuario.id
				  and ra.inicioEm <= :agora
				  and (ra.fimEm is null or ra.fimEm > :agora)
				  and (ra.registroAlvo.id = r.id or\s"""
			+ NORMALIZAR + "ra.categoriaAlvo" + FIM_NORMALIZAR + " = " + NORMALIZAR + "r.categoriaInformacao" + FIM_NORMALIZAR
			+ "))";

	String AUTORIZACAO_ADICIONAL_VIGENTE = """
			exists (select 1 from Consentimento c
				where c.paciente.id = r.prontuario.paciente.id
				  and c.tipo = com.sigh.sigh.project.paciente.entity.TipoConsentimento.ADICIONAL
				  and c.decisao = com.sigh.sigh.project.paciente.entity.DecisaoConsentimento.AUTORIZADO
				  and c.destinataria.id = :instituicaoId
				  and not exists (select 1 from Consentimento revogacao
					where revogacao.anterior = c
					  and revogacao.tipo = com.sigh.sigh.project.paciente.entity.TipoConsentimento.ADICIONAL
					  and revogacao.decisao = com.sigh.sigh.project.paciente.entity.DecisaoConsentimento.REVOGADO)
				  and (c.registroAlvo.id = r.id or\s"""
			+ NORMALIZAR + "c.categoriaAlvo" + FIM_NORMALIZAR + " = " + NORMALIZAR + "r.categoriaInformacao" + FIM_NORMALIZAR
			+ "))";

	@Query("""
			select r from RegistroClinico r
			join fetch r.origem
			where r.prontuario.id = :prontuarioId
			  and (:compartilhado = true or r.origem.id = :instituicaoId)
			  and (not\s""" + RESTRICAO_VIGENTE + " or " + AUTORIZACAO_ADICIONAL_VIGENTE + """
			)
			order by r.criadoEm desc
			""")
	List<RegistroClinico> historicoVisivel(@Param("prontuarioId") UUID prontuarioId,
			@Param("instituicaoId") UUID instituicaoId,
			@Param("compartilhado") boolean compartilhado,
			@Param("agora") LocalDateTime agora,
			Limit limite);

	@Query("""
			select count(r) from RegistroClinico r
			where r.prontuario.id = :prontuarioId
			  and (:compartilhado = true or r.origem.id = :instituicaoId)
			  and\s""" + RESTRICAO_VIGENTE + " and not " + AUTORIZACAO_ADICIONAL_VIGENTE)
	long contarRestritosOcultos(@Param("prontuarioId") UUID prontuarioId,
			@Param("instituicaoId") UUID instituicaoId,
			@Param("compartilhado") boolean compartilhado,
			@Param("agora") LocalDateTime agora);

	@Query("""
			select r from RegistroClinico r
			join fetch r.origem
			where r.prontuario.id = :prontuarioId
			order by r.criadoEm desc
			""")
	List<RegistroClinico> historicoCompleto(@Param("prontuarioId") UUID prontuarioId);

	@Query("select r.id from RegistroClinico r where r.id in :ids"
			+ " and (:compartilhado = true or r.origem.id = :instituicaoId)"
			+ " and (not " + RESTRICAO_VIGENTE + " or " + AUTORIZACAO_ADICIONAL_VIGENTE + ")")
	List<UUID> visiveisEntre(@Param("ids") Collection<UUID> ids, @Param("instituicaoId") UUID instituicaoId,
			@Param("compartilhado") boolean compartilhado, @Param("agora") LocalDateTime agora);

	@Query("select r.id from RegistroClinico r where r.prontuario.id = :prontuarioId and " + RESTRICAO_VIGENTE)
	List<UUID> restritos(@Param("prontuarioId") UUID prontuarioId, @Param("agora") LocalDateTime agora);

	@Query("""
			select r from RegistroClinico r
			join fetch r.autor
			where r.prontuario.id = :prontuarioId
			  and r.tipo = com.sigh.sigh.project.prontuario.entity.TipoRegistro.RESUMO_ALTA
			  and r.criadoEm >= :desde
			order by r.criadoEm desc, r.id
			""")
	List<RegistroClinico> resumosDeAltaDesde(@Param("prontuarioId") UUID prontuarioId, @Param("desde") LocalDateTime desde);
}
