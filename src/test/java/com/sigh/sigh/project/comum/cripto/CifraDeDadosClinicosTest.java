package com.sigh.sigh.project.comum.cripto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RNF-004: AES-256-GCM com IV aleatório, detecção de adulteração e rotação de chave. */
class CifraDeDadosClinicosTest {

	private static final String CHAVE_A = Base64.getEncoder().encodeToString("chave-ficticia-a-de-32-bytes-!!!".getBytes());
	private static final String CHAVE_B = Base64.getEncoder().encodeToString("chave-ficticia-b-de-32-bytes-!!!".getBytes());

	private final CifraDeDadosClinicos cifra = new CifraDeDadosClinicos(CHAVE_A, "1", "");

	@Test
	void cifraEDecifraSemDeixarOTextoClaroNoValorGravado() {
		String gravado = cifra.cifrar("Episódio depressivo grave, F32.2");

		assertThat(gravado).startsWith("sigh:v1:1:").doesNotContain("depressivo").doesNotContain("F32");
		assertThat(cifra.decifrar(gravado)).isEqualTo("Episódio depressivo grave, F32.2");
	}

	@Test
	void mesmoTextoGeraValoresDiferentesACadaVez() {
		assertThat(cifra.cifrar("Asma")).isNotEqualTo(cifra.cifrar("Asma"));
	}

	@Test
	void valorAdulteradoNaoEhDecifrado() {
		String gravado = cifra.cifrar("Alergia a dipirona");
		byte[] pacote = Base64.getDecoder().decode(gravado.substring("sigh:v1:1:".length()));
		pacote[pacote.length - 1] ^= 1;
		String adulterado = "sigh:v1:1:" + Base64.getEncoder().encodeToString(pacote);

		assertThatThrownBy(() -> cifra.decifrar(adulterado))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("dipirona");
	}

	@Test
	void outraChaveNaoDecifraEChaveDesconhecidaEhRecusada() {
		String gravado = cifra.cifrar("Diabetes tipo 2");

		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_B, "1", "").decifrar(gravado))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_B, "2", "").decifrar(gravado))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("chave desconhecida");
	}

	@Test
	void rotacaoMantemAChaveAnteriorSoParaLeitura() {
		String antigo = cifra.cifrar("Hipertensão");
		CifraDeDadosClinicos rotacionada = new CifraDeDadosClinicos(CHAVE_B, "2", "1:" + CHAVE_A);

		assertThat(rotacionada.decifrar(antigo)).isEqualTo("Hipertensão");
		assertThat(rotacionada.cifrar("Hipertensão")).startsWith("sigh:v1:2:");
	}

	@Test
	void valorLegadoEmClaroENuloPassamSemAlteracao() {
		assertThat(cifra.decifrar("Texto gravado antes da criptografia")).isEqualTo("Texto gravado antes da criptografia");
		assertThat(cifra.cifrar(null)).isNull();
		assertThat(cifra.decifrar(null)).isNull();
	}

	@Test
	void chaveForaDoPadraoImpedeAInicializacao() {
		assertThatThrownBy(() -> new CifraDeDadosClinicos("curta", "1", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
		assertThatThrownBy(() -> new CifraDeDadosClinicos(Base64.getEncoder().encodeToString(new byte[16]), "1", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_A, "id com espaço", ""))
				.isInstanceOf(IllegalStateException.class);
	}
}
