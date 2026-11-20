package com.sigh.sigh.project.comum.cripto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca o campo em claro (transiente) de um dado clínico. O valor gravado fica em outro campo, o
 * {@link #gravadoEm()}, mapeado para a coluna com {@code @Column(name = ...)}, e é cifrado e decifrado
 * por {@link CifragemDasEntidades} com o vínculo à tabela, à coluna e à linha (RNF-004).
 *
 * <p>O setter do campo em claro precisa pôr {@link #PENDENTE} no campo gravado: é isso que faz o
 * Hibernate perceber a mudança (inclusive de nulo para preenchido) e o listener cifrar o novo valor antes
 * do UPDATE.
 *
 * <p>O campo em claro só é preenchido quando a entidade termina de carregar ({@code @PostLoad}). Por
 * isso uma consulta JPQL não seleciona o campo, nem passa a entidade para um {@code select new}: o
 * construtor do DTO roda antes do {@code @PostLoad} e veria o campo vazio. Busque a entidade e monte o
 * DTO depois.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cifrado {

	/**
	 * Marca do campo gravado depois de o setter mudar o valor em claro. Nunca chega ao banco: o listener a
	 * troca pelo valor cifrado (ou por nulo) antes do INSERT e do UPDATE.
	 */
	String PENDENTE = "sigh:pendente";

	/** Nome do campo persistido que guarda o valor cifrado. */
	String gravadoEm();

	/**
	 * Para a coluna de tabela imutável (a auditoria, RN-006), que não pode ser regravada: nome do campo
	 * booleano da entidade ({@code @Formula}) que diz se a linha está na lista de legado, gravada na
	 * migração. Só essas linhas continuam lendo o texto claro e o formato antigo.
	 */
	String legadoSe() default "";

	/** Tabela da lista de legado (coluna {@code registro_id}), a mesma da {@code @Formula} de {@link #legadoSe()}. */
	String legadoListadoEm() default "";
}
