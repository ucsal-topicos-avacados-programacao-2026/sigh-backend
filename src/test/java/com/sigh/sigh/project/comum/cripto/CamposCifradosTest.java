package com.sigh.sigh.project.comum.cripto;

import com.sigh.sigh.exemplos.cripto.EntidadesMalConfiguradas;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamposCifradosTest {

	@Test
	void setterQueNaoMarcaOCampoGravadoComoPendenteImpedeASubida() {
		assertThatThrownBy(() -> CamposCifrados.de(EntidadesMalConfiguradas.SetterQueNaoLimpa.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("setTexto precisa pôr Cifrado.PENDENTE em textoCifrado");
	}

	@Test
	void campoCifradoEmSuperclasseImpedeASubida() {
		assertThatThrownBy(() -> CamposCifrados.de(EntidadesMalConfiguradas.HerdaCampoCifrado.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("superclasse não é suportado");
	}

	@Test
	void campoCifradoSemSetterImpedeASubida() {
		assertThatThrownBy(() -> CamposCifrados.de(EntidadesMalConfiguradas.SemSetter.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("precisa de setTexto(String)");
	}

	@Test
	void entidadeComCampoCifradoSemOListenerImpedeASubida() {
		assertThatThrownBy(() -> CamposCifrados.de(EntidadesMalConfiguradas.SemListener.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("@EntityListeners(CifragemDasEntidades.class)");
	}

	@Test
	void campoEmClaroQueSeriaPersistidoImpedeASubida() {
		assertThatThrownBy(() -> CamposCifrados.de(EntidadesMalConfiguradas.CampoEmClaroPersistido.class))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("precisa ser @Transient");
	}

	@Test
	void entidadeBemConfiguradaPassa() {
		assertThat(CamposCifrados.de(EntidadesMalConfiguradas.BemConfigurada.class).campos())
				.singleElement().satisfies(campo -> assertThat(campo.nome()).isEqualTo("exemplo.texto"));
	}
}
