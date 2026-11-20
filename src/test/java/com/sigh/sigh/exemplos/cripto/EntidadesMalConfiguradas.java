package com.sigh.sigh.exemplos.cripto;

import com.sigh.sigh.project.comum.cripto.Cifrado;
import com.sigh.sigh.project.comum.cripto.CifragemDasEntidades;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.UUID;

/**
 * Entidades de exemplo com campo cifrado mal configurado, para o CamposCifradosTest. Ficam fora do
 * pacote da aplicação para o Hibernate e a varredura de entidades não as encontrarem.
 */
public final class EntidadesMalConfiguradas {

	private EntidadesMalConfiguradas() {
	}

	/** Setter gerado do jeito do Lombok: não marca o campo gravado como pendente, e a alteração se perderia. */
	@Entity
	@EntityListeners(CifragemDasEntidades.class)
	@Table(name = "exemplo")
	public static class SetterQueNaoLimpa {
		@Id
		private UUID id;
		@Transient
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;

		public void setTexto(String texto) {
			this.texto = texto;
		}
	}

	@Entity
	@Table(name = "exemplo")
	public static class SemListener {
		@Id
		private UUID id;
		@Transient
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;

		public void setTexto(String texto) {
			this.texto = texto;
			this.textoCifrado = Cifrado.PENDENTE;
		}
	}

	@Entity
	@EntityListeners(CifragemDasEntidades.class)
	@Table(name = "exemplo")
	public static class CampoEmClaroPersistido {
		@Id
		private UUID id;
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;
	}

	@Entity
	@EntityListeners(CifragemDasEntidades.class)
	@Table(name = "exemplo")
	public static class BemConfigurada {
		@Id
		private UUID id;
		@Transient
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;

		public void setTexto(String texto) {
			this.texto = texto;
			this.textoCifrado = Cifrado.PENDENTE;
		}
	}

	/** Superclasse com campo cifrado: o listener só olha a entidade, então o campo nunca seria gravado. */
	@jakarta.persistence.MappedSuperclass
	public static class BaseComCampoCifrado {
		@Transient
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;
	}

	@Entity
	@EntityListeners(CifragemDasEntidades.class)
	@Table(name = "exemplo")
	public static class HerdaCampoCifrado extends BaseComCampoCifrado {
		@Id
		private UUID id;
	}

	/** Sem setter: o único jeito de mudar o valor seria por fora do contrato do PENDENTE. */
	@Entity
	@EntityListeners(CifragemDasEntidades.class)
	@Table(name = "exemplo")
	public static class SemSetter {
		@Id
		private UUID id;
		@Transient
		@Cifrado(gravadoEm = "textoCifrado")
		private String texto;
		@Column(name = "texto")
		private String textoCifrado;
	}
}
