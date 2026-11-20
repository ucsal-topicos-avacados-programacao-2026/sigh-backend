package com.sigh.sigh.project.comum.cripto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Criptografia dos dados clínicos em repouso (RNF-004): AES-256-GCM, com IV aleatório por valor e
 * etiqueta de autenticação, então um valor adulterado não é decifrado. A chave vem do ambiente
 * ({@code SIGH_CHAVE_DADOS}), nunca do código, e só o back-end a conhece: banco, cópias de segurança e
 * quem os opera veem apenas o texto cifrado.
 *
 * <p>Formato gravado: {@code sigh:v2:<idDaChave>:<base64(iv || texto cifrado || etiqueta)>}. O valor
 * fica preso à tabela, à coluna e à linha onde foi gravado (dado autenticado adicional do GCM): copiado
 * para outra linha ou outra coluna, ele não é decifrado. O id da chave permite trocar a chave atual
 * mantendo as anteriores só para leitura ({@code sigh.cripto.chaves-anteriores}).
 *
 * <p>O formato {@code sigh:v1:} (BE-09, sem vínculo à linha) e o texto claro anterior à criptografia
 * só são lidos por {@link #decifrarLegado}: pela migração ({@link ManutencaoDaCifragem}) e pela coluna
 * imutável da auditoria, que não pode ser regravada.
 */
@Component
public class CifraDeDadosClinicos {

	static final String PREFIXO = "sigh:v2:";
	static final String PREFIXO_V1 = "sigh:v1:";
	private static final String ALGORITMO = "AES/GCM/NoPadding";
	private static final int TAMANHO_IV = 12;
	private static final int TAMANHO_ETIQUETA_BITS = 128;
	private static final int TAMANHO_CHAVE = 32;

	private final SecureRandom aleatorio = new SecureRandom();
	private final String idDaChaveAtual;
	private final Map<String, SecretKey> chaves = new HashMap<>();

	public CifraDeDadosClinicos(@Value("${sigh.cripto.chave}") String chaveAtual,
			@Value("${sigh.cripto.chave-id:1}") String idDaChaveAtual,
			@Value("${sigh.cripto.chaves-anteriores:}") String chavesAnteriores) {
		this.idDaChaveAtual = validarId(idDaChaveAtual);
		chaves.put(this.idDaChaveAtual, chave(chaveAtual, "sigh.cripto.chave"));
		for (String par : chavesAnteriores.split(",")) {
			if (par.isBlank()) {
				continue;
			}
			String[] partes = par.strip().split(":", 2);
			if (partes.length != 2) {
				throw new IllegalStateException("sigh.cripto.chaves-anteriores deve ter o formato id:chaveBase64,...");
			}
			if (chaves.putIfAbsent(validarId(partes[0]), chave(partes[1], "sigh.cripto.chaves-anteriores")) != null) {
				throw new IllegalStateException("Id de chave repetido em sigh.cripto.chaves-anteriores.");
			}
		}
	}

	/**
	 * Mesma cifra a partir das propriedades. O listener JPA usa este caminho porque o Hibernate o cria
	 * também em testes de repositório, que não carregam os componentes da aplicação.
	 */
	public static CifraDeDadosClinicos doAmbiente(Environment ambiente) {
		return new CifraDeDadosClinicos(ambiente.getRequiredProperty("sigh.cripto.chave"),
				ambiente.getProperty("sigh.cripto.chave-id", "1"), ambiente.getProperty("sigh.cripto.chaves-anteriores", ""));
	}

	/** Valor no formato atual, de qualquer chave conhecida ou não. */
	public static boolean noFormatoAtual(String valor) {
		return valor != null && valor.startsWith(PREFIXO);
	}

	/** Prefixo dos valores gravados com a chave atual: o que não o tem precisa ser regravado. */
	public String prefixoDaChaveAtual() {
		return PREFIXO + idDaChaveAtual + ":";
	}

	/**
	 * Cifra o valor da {@code coluna} ("tabela.coluna") na linha {@code linha}. Só decifra com os
	 * mesmos três.
	 */
	public String cifrar(String textoClaro, String coluna, UUID linha) {
		if (textoClaro == null) {
			return null;
		}
		try {
			byte[] iv = new byte[TAMANHO_IV];
			aleatorio.nextBytes(iv);
			Cipher cifra = Cipher.getInstance(ALGORITMO);
			cifra.init(Cipher.ENCRYPT_MODE, chaves.get(idDaChaveAtual), new GCMParameterSpec(TAMANHO_ETIQUETA_BITS, iv));
			cifra.updateAAD(vinculo(coluna, linha));
			byte[] cifrado = cifra.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));
			byte[] pacote = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return prefixoDaChaveAtual() + Base64.getEncoder().encodeToString(pacote);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Falha ao cifrar dado clínico.", e);
		}
	}

	/**
	 * Decifra um valor no formato atual. Valor de outra linha ou coluna, adulterado, de chave
	 * desconhecida, em claro ou no formato antigo é recusado, sem revelar nada do conteúdo.
	 */
	public String decifrar(String valor, String coluna, UUID linha) {
		if (valor == null) {
			return null;
		}
		if (!noFormatoAtual(valor)) {
			throw new DadoClinicoIlegivelException(coluna, linha, "valor sem a cifra atual");
		}
		return abrir(valor.substring(PREFIXO.length()), vinculo(coluna, linha), coluna, linha);
	}

	/**
	 * Lê o que veio antes do formato atual: o {@code sigh:v1:} do BE-09 (sem vínculo à linha) ou o texto
	 * claro gravado antes da criptografia, que volta como está. Não lê o formato atual.
	 */
	public String decifrarLegado(String valor, String coluna, UUID linha) {
		if (valor == null || !valor.startsWith(PREFIXO_V1)) {
			if (noFormatoAtual(valor)) {
				throw new DadoClinicoIlegivelException(coluna, linha, "valor no formato atual lido como legado");
			}
			return valor;
		}
		return abrir(valor.substring(PREFIXO_V1.length()), null, coluna, linha);
	}

	private String abrir(String resto, byte[] vinculo, String coluna, UUID linha) {
		int separador = resto.indexOf(':');
		SecretKey chave = separador > 0 ? chaves.get(resto.substring(0, separador)) : null;
		if (chave == null) {
			throw new DadoClinicoIlegivelException(coluna, linha, "chave desconhecida");
		}
		try {
			byte[] pacote = Base64.getDecoder().decode(resto.substring(separador + 1));
			if (pacote.length < TAMANHO_IV + TAMANHO_ETIQUETA_BITS / 8) {
				throw new DadoClinicoIlegivelException(coluna, linha, "valor cifrado inválido");
			}
			Cipher cifra = Cipher.getInstance(ALGORITMO);
			cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_ETIQUETA_BITS, pacote, 0, TAMANHO_IV));
			if (vinculo != null) {
				cifra.updateAAD(vinculo);
			}
			return new String(cifra.doFinal(pacote, TAMANHO_IV, pacote.length - TAMANHO_IV), StandardCharsets.UTF_8);
		} catch (AEADBadTagException e) {
			throw new DadoClinicoIlegivelException(coluna, linha, "valor alterado, de outra linha ou de outra chave");
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new DadoClinicoIlegivelException(coluna, linha, "valor cifrado inválido");
		}
	}

	/** Dado autenticado adicional: versão do formato, coluna e linha. */
	private static byte[] vinculo(String coluna, UUID linha) {
		if (coluna == null || coluna.isBlank() || linha == null) {
			throw new IllegalStateException("Dado clínico cifrado precisa de coluna e linha.");
		}
		return ("sigh:v2|" + coluna + "|" + linha).getBytes(StandardCharsets.UTF_8);
	}

	private static SecretKey chave(String base64, String propriedade) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.strip());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(propriedade + " precisa estar em Base64.", e);
		}
		if (bytes.length != TAMANHO_CHAVE) {
			throw new IllegalStateException(propriedade + " precisa ter 32 bytes (AES-256); gere com: openssl rand -base64 32");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	private static String validarId(String id) {
		String limpo = id == null ? "" : id.strip();
		if (!limpo.matches("[A-Za-z0-9_-]{1,20}")) {
			throw new IllegalStateException("Id da chave de dados inválido.");
		}
		return limpo;
	}
}
