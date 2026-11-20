package com.sigh.sigh.project.comum.cripto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF-004: AES-256-GCM com IV aleatório, detecção de adulteração, rotação de chave e valor preso à
 * coluna e à linha (BE-22).
 */
class CifraDeDadosClinicosTest {

	private static final byte[] BYTES_A = "chave-ficticia-a-de-32-bytes-!!!".getBytes(StandardCharsets.UTF_8);
	private static final String CHAVE_A = Base64.getEncoder().encodeToString(BYTES_A);
	private static final String CHAVE_B = Base64.getEncoder().encodeToString("chave-ficticia-b-de-32-bytes-!!!".getBytes());
	private static final String CONTEUDO = "registro_clinico.conteudo";
	private static final UUID LINHA = UUID.randomUUID();

	private final CifraDeDadosClinicos cifra = new CifraDeDadosClinicos(CHAVE_A, "1", "");

	@Test
	void cifraEDecifraSemDeixarOTextoClaroNoValorGravado() {
		String gravado = cifra.cifrar("Episódio depressivo grave, F32.2", CONTEUDO, LINHA);

		assertThat(gravado).startsWith("sigh:v2:1:").doesNotContain("depressivo").doesNotContain("F32");
		assertThat(cifra.decifrar(gravado, CONTEUDO, LINHA)).isEqualTo("Episódio depressivo grave, F32.2");
	}

	@Test
	void mesmoTextoGeraValoresDiferentesACadaVez() {
		assertThat(cifra.cifrar("Asma", CONTEUDO, LINHA)).isNotEqualTo(cifra.cifrar("Asma", CONTEUDO, LINHA));
	}

	/** Critério de aceite do BE-22: valor copiado de outra linha (ou de outra coluna) não é decifrado. */
	@Test
	void valorCopiadoParaOutraLinhaOuOutraColunaNaoEhDecifrado() {
		String gravado = cifra.cifrar("HIV positivo", CONTEUDO, LINHA);

		assertThatThrownBy(() -> cifra.decifrar(gravado, CONTEUDO, UUID.randomUUID()))
				.isInstanceOf(DadoClinicoIlegivelException.class)
				.hasMessageContaining("de outra linha")
				.hasMessageNotContaining("HIV");
		assertThatThrownBy(() -> cifra.decifrar(gravado, "registro_clinico.codigo_cid10", LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class);
		assertThatThrownBy(() -> cifra.decifrar(gravado, "usuario.mfa_segredo", LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class);
	}

	@Test
	void valorAdulteradoNaoEhDecifrado() {
		String gravado = cifra.cifrar("Alergia a dipirona", CONTEUDO, LINHA);
		byte[] pacote = Base64.getDecoder().decode(gravado.substring("sigh:v2:1:".length()));
		pacote[pacote.length - 1] ^= 1;
		String adulterado = "sigh:v2:1:" + Base64.getEncoder().encodeToString(pacote);

		assertThatThrownBy(() -> cifra.decifrar(adulterado, CONTEUDO, LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class)
				.hasMessageNotContaining("dipirona");
	}

	@Test
	void outraChaveNaoDecifraEChaveDesconhecidaEhRecusada() {
		String gravado = cifra.cifrar("Diabetes tipo 2", CONTEUDO, LINHA);

		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_B, "1", "").decifrar(gravado, CONTEUDO, LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class);
		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_B, "2", "").decifrar(gravado, CONTEUDO, LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class)
				.hasMessageContaining("chave desconhecida");
	}

	@Test
	void rotacaoMantemAChaveAnteriorSoParaLeitura() {
		String antigo = cifra.cifrar("Hipertensão", CONTEUDO, LINHA);
		CifraDeDadosClinicos rotacionada = new CifraDeDadosClinicos(CHAVE_B, "2", "1:" + CHAVE_A);

		assertThat(rotacionada.decifrar(antigo, CONTEUDO, LINHA)).isEqualTo("Hipertensão");
		assertThat(rotacionada.cifrar("Hipertensão", CONTEUDO, LINHA)).startsWith("sigh:v2:2:");
		assertThat(rotacionada.prefixoDaChaveAtual()).isEqualTo("sigh:v2:2:");
	}

	/**
	 * Critério de aceite do BE-22: texto claro gravado direto no banco não é aceito como dado. O formato
	 * antigo, sem vínculo à linha, também não: só a leitura de legado (migração e auditoria) os lê.
	 */
	@Test
	void textoClaroEFormatoAntigoSoSaoLidosComoLegado() throws Exception {
		String v1 = formatoAntigo("Tuberculose");

		assertThatThrownBy(() -> cifra.decifrar("Texto gravado direto no banco", CONTEUDO, LINHA))
				.isInstanceOf(DadoClinicoIlegivelException.class)
				.hasMessageContaining("sem a cifra atual")
				.hasMessageNotContaining("Texto gravado");
		assertThatThrownBy(() -> cifra.decifrar(v1, CONTEUDO, LINHA)).isInstanceOf(DadoClinicoIlegivelException.class);

		assertThat(cifra.decifrarLegado("Texto gravado antes da criptografia", CONTEUDO, LINHA))
				.isEqualTo("Texto gravado antes da criptografia");
		assertThat(cifra.decifrarLegado(v1, CONTEUDO, LINHA)).isEqualTo("Tuberculose");
		String atual = cifra.cifrar("Asma", CONTEUDO, LINHA);
		assertThatThrownBy(() -> cifra.decifrarLegado(atual, CONTEUDO, LINHA))
				.as("o formato atual nunca é lido sem o vínculo")
				.isInstanceOf(DadoClinicoIlegivelException.class);
	}

	@Test
	void nuloPassaSemAlteracao() {
		assertThat(cifra.cifrar(null, CONTEUDO, LINHA)).isNull();
		assertThat(cifra.decifrar(null, CONTEUDO, LINHA)).isNull();
		assertThat(cifra.decifrarLegado(null, CONTEUDO, LINHA)).isNull();
	}

	@Test
	void semColunaOuLinhaNaoCifra() {
		assertThatThrownBy(() -> cifra.cifrar("Asma", CONTEUDO, null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cifra.cifrar("Asma", " ", LINHA)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void chaveForaDoPadraoImpedeAInicializacao() {
		assertThatThrownBy(() -> new CifraDeDadosClinicos("curta", "1", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
		assertThatThrownBy(() -> new CifraDeDadosClinicos(Base64.getEncoder().encodeToString(new byte[16]), "1", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_A, "id com espaço", ""))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CifraDeDadosClinicos(CHAVE_A, "1", "1:" + CHAVE_B))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("repetido");
	}

	/** Valor como o BE-09 gravava: sigh:v1, AES-GCM sem dado autenticado adicional. */
	static String formatoAntigo(String texto) throws Exception {
		byte[] iv = new byte[12];
		Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(BYTES_A, "AES"), new GCMParameterSpec(128, iv));
		byte[] cifrado = aes.doFinal(texto.getBytes(StandardCharsets.UTF_8));
		return "sigh:v1:1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + cifrado.length).put(iv).put(cifrado).array());
	}
}
