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

	/**
	 * Categoria é texto livre: a comparação colapsa espaços em branco, tira os das pontas e ignora acentos e
	 * maiúsculas, para que "saude  MENTAL" e "Saúde mental" sejam a mesma categoria. A mesma regra está em
	 * {@link com.sigh.sigh.project.paciente.service.CategoriaDeInformacao} e no índice da V4.
	 */
	String ACENTOS = "ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç";
	String SEM_ACENTOS = "AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc";
	String NORMALIZAR = "lower(function('translate', trim(function('regexp_replace', ";
	String FIM_NORMALIZAR = ", '\\s+', ' ', 'g')), '" + ACENTOS + "', '" + SEM_ACENTOS + "'))";

	/** O registro está coberto por uma restrição vigente do paciente (RF-009): ele próprio ou a categoria dele. */
	String RESTRICAO_VIGENTE = """
			exists (select 1 from RestricaoAcesso ra
				where ra.prontuario.id = r.prontuario.id
				  and ra.inicioEm <= :agora
				  and (ra.fimEm is null or ra.fimEm > :agora)
				  and (ra.registroAlvo.id = r.id or\s"""
			+ NORMALIZAR + "ra.categoriaAlvo" + FIM_NORMALIZAR + " = " + NORMALIZAR + "r.categoriaInformacao" + FIM_NORMALIZAR
			+ "))";

	/**
	 * O paciente deu à instituição uma autorização adicional (RF-009) que cobre o registro e ainda não
	 * a revogou. A revogação é uma nova versão do consentimento que aponta para a autorização.
	 */
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

	/**
	 * Registros que a instituição pode ver sem acesso de emergência: os que ela mesma produziu e, se o
	 * paciente consente com o compartilhamento, os de toda a rede. Registros restritos pelo paciente
	 * ficam de fora, salvo autorização adicional para a instituição.
	 */
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

	/** Quantos registros a instituição veria se não estivessem restritos pelo paciente. */
	@Query("""
			select count(r) from RegistroClinico r
			where r.prontuario.id = :prontuarioId
			  and (:compartilhado = true or r.origem.id = :instituicaoId)
			  and\s""" + RESTRICAO_VIGENTE + " and not " + AUTORIZACAO_ADICIONAL_VIGENTE)
	long contarRestritosOcultos(@Param("prontuarioId") UUID prontuarioId,
			@Param("instituicaoId") UUID instituicaoId,
			@Param("compartilhado") boolean compartilhado,
			@Param("agora") LocalDateTime agora);

	/** Prontuário inteiro, do mais recente para o mais antigo: acesso de emergência (RN-003) e o próprio paciente. */
	@Query("""
			select r from RegistroClinico r
			join fetch r.origem
			where r.prontuario.id = :prontuarioId
			order by r.criadoEm desc
			""")
	List<RegistroClinico> historicoCompleto(@Param("prontuarioId") UUID prontuarioId);

	/** Dos registros informados, os que a instituição ainda pode ver sem emergência (mesma regra de historicoVisivel). */
	@Query("select r.id from RegistroClinico r where r.id in :ids"
			+ " and (:compartilhado = true or r.origem.id = :instituicaoId)"
			+ " and (not " + RESTRICAO_VIGENTE + " or " + AUTORIZACAO_ADICIONAL_VIGENTE + ")")
	List<UUID> visiveisEntre(@Param("ids") Collection<UUID> ids, @Param("instituicaoId") UUID instituicaoId,
			@Param("compartilhado") boolean compartilhado, @Param("agora") LocalDateTime agora);

	/** Registros do prontuário cobertos por restrição vigente. */
	@Query("select r.id from RegistroClinico r where r.prontuario.id = :prontuarioId and " + RESTRICAO_VIGENTE)
	List<UUID> restritos(@Param("prontuarioId") UUID prontuarioId, @Param("agora") LocalDateTime agora);

	/** Resumos de alta do prontuário feitos desde a admissão, para o administrativo anexar à alta (FE-04). */
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
