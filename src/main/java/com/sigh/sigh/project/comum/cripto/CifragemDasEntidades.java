package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.UUID;

/**
 * Listener JPA que cifra os campos {@link Cifrado} antes do INSERT e do UPDATE e os decifra ao
 * carregar a entidade, com o vínculo à tabela, à coluna e ao id da linha (RNF-004). No Hibernate desta
 * versão o id UUID já foi gerado quando o {@code @PrePersist} roda.
 *
 * <p>Na leitura, um valor que não decifra (em claro, no formato antigo, adulterado ou copiado de outra
 * linha) é recusado com {@link DadoClinicoIlegivelException} e registrado no log, sem o conteúdo. A
 * única exceção é a coluna imutável da auditoria, que continua lendo o legado só das linhas que a
 * migração listou.
 */
public class CifragemDasEntidades {

	private static final Logger log = LoggerFactory.getLogger(CifragemDasEntidades.class);

	private final CifraDeDadosClinicos cifra;

	public CifragemDasEntidades(Environment ambiente) {
		this.cifra = CifraDeDadosClinicos.doAmbiente(ambiente);
	}

	CifragemDasEntidades(CifraDeDadosClinicos cifra) {
		this.cifra = cifra;
	}

	@PrePersist
	@PreUpdate
	void cifrar(Object entidade) {
		CamposCifrados campos = CamposCifrados.de(entidade.getClass());
		UUID id = campos.id(entidade);
		for (CamposCifrados.Campo campo : campos.campos()) {
			Object dono = campo.dono(entidade);
			if (dono == null) {
				continue;
			}
			Object claro = CamposCifrados.ler(campo.claro(), dono);
			Object gravado = CamposCifrados.ler(campo.gravado(), dono);
			if (claro == null) {
				CamposCifrados.escrever(campo.gravado(), dono, null);
			} else if (gravado == null || Cifrado.PENDENTE.equals(gravado)) {
				CamposCifrados.escrever(campo.gravado(), dono, cifra.cifrar(campo.paraTexto(claro), campo.nome(), id));
			}
		}
	}

	@PostLoad
	void decifrar(Object entidade) {
		CamposCifrados campos = CamposCifrados.de(entidade.getClass());
		UUID id = campos.id(entidade);
		for (CamposCifrados.Campo campo : campos.campos()) {
			Object dono = campo.dono(entidade);
			if (dono == null) {
				continue;
			}
			String gravado = (String) CamposCifrados.ler(campo.gravado(), dono);
			CamposCifrados.escrever(campo.claro(), dono, campo.deTexto(ler(gravado, campo, dono, id)));
		}
	}

	private String ler(String gravado, CamposCifrados.Campo campo, Object dono, UUID id) {
		try {
			if (campo.legadoImutavel() && !CifraDeDadosClinicos.noFormatoAtual(gravado)
					&& Boolean.TRUE.equals(CamposCifrados.ler(campo.legadoSe(), dono))) {
				return cifra.decifrarLegado(gravado, campo.nome(), id);
			}
			return cifra.decifrar(gravado, campo.nome(), id);
		} catch (DadoClinicoIlegivelException e) {
			log.error("Leitura recusada: {}", e.getMessage());
			throw e;
		}
	}
}
