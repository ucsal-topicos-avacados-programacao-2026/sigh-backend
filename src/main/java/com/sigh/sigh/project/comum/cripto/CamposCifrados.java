package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Formula;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Os campos cifrados ({@link Cifrado}) de uma entidade, inclusive os de objetos embutidos, com a coluna
 * de cada um. É a mesma lista que o listener JPA e a manutenção da cifragem (que trabalha direto no
 * banco) usam, então os dois sempre concordam sobre qual coluna é cifrada e com qual vínculo.
 */
public final class CamposCifrados {

	private static final String PACOTE_DAS_ENTIDADES = "com.sigh.sigh.project";
	private static final Map<Class<?>, CamposCifrados> POR_ENTIDADE = new ConcurrentHashMap<>();
	private static volatile List<Campo> daAplicacao;

	private final Field id;
	private final List<Campo> campos;

	/**
	 * Um campo cifrado. {@code embutidoEm} é o campo da entidade que guarda o objeto embutido, ou nulo
	 * quando o campo é da própria entidade.
	 */
	public record Campo(String tabela, String coluna, Field claro, Field gravado, Field embutidoEm, Field legadoSe,
			String legadoListadoEm) {

		/** Coluna de tabela imutável: nunca regravada, lê o legado só das linhas listadas na migração. */
		public boolean legadoImutavel() {
			return legadoSe != null;
		}

		/** "tabela.coluna": entra no vínculo do valor cifrado. */
		public String nome() {
			return tabela + "." + coluna;
		}

		Object dono(Object entidade) {
			return embutidoEm == null ? entidade : ler(embutidoEm, entidade);
		}

		String paraTexto(Object valor) {
			if (valor instanceof BigDecimal decimal) {
				return decimal.toPlainString();
			}
			return valor.toString();
		}

		Object deTexto(String texto) {
			if (texto == null || claro.getType() == String.class) {
				return texto;
			}
			if (claro.getType() == Integer.class) {
				return Integer.valueOf(texto);
			}
			if (claro.getType() == BigDecimal.class) {
				return new BigDecimal(texto);
			}
			throw new IllegalStateException("Tipo não suportado em campo cifrado: " + claro);
		}
	}

	private CamposCifrados(Class<?> entidade) {
		String tabela = entidade.getAnnotation(Table.class) != null ? entidade.getAnnotation(Table.class).name() : "";
		Field idDaEntidade = null;
		List<Campo> encontrados = new ArrayList<>();
		for (Field campo : entidade.getDeclaredFields()) {
			if (campo.isAnnotationPresent(Id.class)) {
				idDaEntidade = acessivel(campo);
			}
			if (campo.isAnnotationPresent(Embedded.class) || campo.getType().isAnnotationPresent(Embeddable.class)) {
				for (Field embutido : campo.getType().getDeclaredFields()) {
					cifrado(tabela, campo.getType(), embutido, acessivel(campo)).ifPresent(encontrados::add);
				}
			}
			cifrado(tabela, entidade, campo, null).ifPresent(encontrados::add);
		}
		for (Class<?> acima = entidade.getSuperclass(); acima != null && acima != Object.class; acima = acima.getSuperclass()) {
			if (Arrays.stream(acima.getDeclaredFields()).anyMatch(campo -> campo.isAnnotationPresent(Cifrado.class))) {
				throw new IllegalStateException(acima.getSimpleName() + ": campo cifrado em superclasse não é suportado;"
						+ " declare-o na entidade.");
			}
		}
		if (!encontrados.isEmpty()) {
			if (tabela.isBlank() || idDaEntidade == null || idDaEntidade.getType() != UUID.class) {
				throw new IllegalStateException(entidade.getSimpleName() + " tem campo cifrado: precisa de @Table(name) e id UUID.");
			}
			EntityListeners listeners = entidade.getAnnotation(EntityListeners.class);
			if (listeners == null || !Arrays.asList(listeners.value()).contains(CifragemDasEntidades.class)) {
				throw new IllegalStateException(entidade.getSimpleName()
						+ " tem campo cifrado: precisa de @EntityListeners(CifragemDasEntidades.class).");
			}
		}
		this.id = idDaEntidade;
		this.campos = List.copyOf(encontrados);
	}

	private static Optional<Campo> cifrado(String tabela, Class<?> dono, Field claro, Field embutidoEm) {
		Cifrado marca = claro.getAnnotation(Cifrado.class);
		if (marca == null) {
			return Optional.empty();
		}
		if (!claro.isAnnotationPresent(Transient.class)) {
			throw new IllegalStateException(claro + ": o campo em claro precisa ser @Transient.");
		}
		Field gravado;
		try {
			gravado = dono.getDeclaredField(marca.gravadoEm());
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(claro + ": campo gravado " + marca.gravadoEm() + " não existe.", e);
		}
		Column coluna = gravado.getAnnotation(Column.class);
		if (gravado.getType() != String.class || coluna == null || coluna.name().isBlank()) {
			throw new IllegalStateException(gravado + ": o campo gravado precisa ser String com @Column(name).");
		}
		conferirSetter(dono, acessivel(claro), acessivel(gravado));
		Field legadoSe = null;
		if (!marca.legadoSe().isBlank()) {
			try {
				legadoSe = acessivel(dono.getDeclaredField(marca.legadoSe()));
			} catch (NoSuchFieldException e) {
				throw new IllegalStateException(claro + ": campo " + marca.legadoSe() + " não existe.", e);
			}
			Formula formula = legadoSe.getAnnotation(Formula.class);
			if (legadoSe.getType() != boolean.class || formula == null || marca.legadoListadoEm().isBlank()
					|| !formula.value().contains(marca.legadoListadoEm())) {
				throw new IllegalStateException(legadoSe + ": precisa ser boolean com @Formula sobre " + marca.legadoListadoEm());
			}
		}
		return Optional.of(new Campo(tabela, coluna.name(), claro, gravado, embutidoEm, legadoSe, marca.legadoListadoEm()));
	}

	/**
	 * O setter do campo em claro precisa pôr {@link Cifrado#PENDENTE} no campo gravado; sem isso, uma
	 * alteração (inclusive de nulo para preenchido) não deixa a entidade suja, o Hibernate não faz o
	 * UPDATE e a mudança se perde sem aviso. Confere numa instância
	 * nova, ao montar os metadados (na inicialização da aplicação).
	 */
	private static void conferirSetter(Class<?> dono, Field claro, Field gravado) {
		String nome = "set" + Character.toUpperCase(claro.getName().charAt(0)) + claro.getName().substring(1);
		Method setter;
		try {
			setter = dono.getMethod(nome, claro.getType());
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(dono.getSimpleName() + " precisa de " + nome + "(" + claro.getType().getSimpleName()
					+ ") pondo Cifrado.PENDENTE em " + gravado.getName() + ": é o único jeito seguro de mudar o valor.", e);
		}
		Object exemplo = claro.getType() == Integer.class ? Integer.valueOf(1)
				: claro.getType() == BigDecimal.class ? BigDecimal.ONE : "exemplo";
		try {
			Constructor<?> construtor = dono.getDeclaredConstructor();
			construtor.setAccessible(true);
			Object instancia = construtor.newInstance();
			setter.invoke(instancia, exemplo);
			if (!Cifrado.PENDENTE.equals(gravado.get(instancia))) {
				throw new IllegalStateException(dono.getSimpleName() + "." + nome + " precisa pôr Cifrado.PENDENTE em "
						+ gravado.getName() + ": sem isso a alteração não é gravada.");
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Não foi possível conferir " + dono.getSimpleName() + "." + nome + ".", e);
		}
	}

	/** Campos cifrados da entidade (vazio se ela não tiver nenhum). */
	public static CamposCifrados de(Class<?> classe) {
		Class<?> entidade = classe;
		while (entidade != null && !entidade.isAnnotationPresent(Entity.class)) {
			entidade = entidade.getSuperclass();
		}
		if (entidade == null) {
			throw new IllegalStateException(classe + " não é entidade JPA.");
		}
		return POR_ENTIDADE.computeIfAbsent(entidade, CamposCifrados::new);
	}

	/** Todos os campos cifrados das entidades da aplicação, em ordem de tabela e coluna. */
	public static List<Campo> daAplicacao() {
		List<Campo> lista = daAplicacao;
		if (lista == null) {
			ClassPathScanningCandidateComponentProvider busca = new ClassPathScanningCandidateComponentProvider(false);
			busca.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
			List<Campo> todos = new ArrayList<>();
			for (BeanDefinition candidata : busca.findCandidateComponents(PACOTE_DAS_ENTIDADES)) {
				try {
					todos.addAll(de(Class.forName(candidata.getBeanClassName())).campos());
				} catch (ClassNotFoundException e) {
					throw new IllegalStateException(e);
				}
			}
			todos.sort(Comparator.comparing(Campo::nome));
			lista = List.copyOf(todos);
			daAplicacao = lista;
		}
		return lista;
	}

	public List<Campo> campos() {
		return campos;
	}

	UUID id(Object entidade) {
		return (UUID) ler(id, entidade);
	}

	static Object ler(Field campo, Object objeto) {
		try {
			return campo.get(objeto);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	static void escrever(Field campo, Object objeto, Object valor) {
		try {
			campo.set(objeto, valor);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field acessivel(Field campo) {
		campo.setAccessible(true);
		return campo;
	}
}
