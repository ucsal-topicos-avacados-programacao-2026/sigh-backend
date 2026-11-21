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

	public static CifraDeDadosClinicos doAmbiente(Environment ambiente) {
		return new CifraDeDadosClinicos(ambiente.getRequiredProperty("sigh.cripto.chave"),
				ambiente.getProperty("sigh.cripto.chave-id", "1"), ambiente.getProperty("sigh.cripto.chaves-anteriores", ""));
	}

	public static boolean noFormatoAtual(String valor) {
		return valor != null && valor.startsWith(PREFIXO);
	}

	public String prefixoDaChaveAtual() {
		return PREFIXO + idDaChaveAtual + ":";
	}

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

	public String decifrar(String valor, String coluna, UUID linha) {
		if (valor == null) {
			return null;
		}
		if (!noFormatoAtual(valor)) {
			throw new DadoClinicoIlegivelException(coluna, linha, "valor sem a cifra atual");
		}
		return abrir(valor.substring(PREFIXO.length()), vinculo(coluna, linha), coluna, linha);
	}

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
