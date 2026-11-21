package com.sigh.sigh.project.comum.cripto;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.UUID;

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
