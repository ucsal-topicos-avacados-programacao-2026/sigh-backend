package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.ia.dto.ClassificacaoIA;
import com.sigh.sigh.project.ia.dto.FormatoRespostaIA;
import com.sigh.sigh.project.ia.dto.MensagemChatIA;
import com.sigh.sigh.project.ia.dto.PedidoChatIA;
import com.sigh.sigh.project.ia.dto.RespostaChatIA;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class ClienteIA {

	private static final Logger log = LoggerFactory.getLogger(ClienteIA.class);
	private static final String CAMINHO_CHAT = "/v1/chat/completions";
	private static final String RACIOCINIO_DESLIGADO = "none";

	private static final String INSTRUCOES_AQUECIMENTO = "Responda apenas: ok";

	private final RestClient clienteHttpIA;
	private final RestClient clienteHttpIAResumo;
	private final IAProperties iaProperties;
	private final JsonMapper leitorJson;
	private final String instrucoesTriagem;
	private final String instrucoesResumo;

	public ClienteIA(@Qualifier("clienteHttpIA") RestClient clienteHttpIA,
			@Qualifier("clienteHttpIAResumo") RestClient clienteHttpIAResumo, IAProperties iaProperties,
			JsonMapper leitorJson) throws IOException {
		this.clienteHttpIA = clienteHttpIA;
		this.clienteHttpIAResumo = clienteHttpIAResumo;
		this.iaProperties = iaProperties;
		this.leitorJson = leitorJson;
		this.instrucoesTriagem = iaProperties.instrucoesTriagem().getContentAsString(StandardCharsets.UTF_8);
		this.instrucoesResumo = iaProperties.instrucoesResumo().getContentAsString(StandardCharsets.UTF_8);
	}

	public CorRisco classificarRisco(String descricaoDoCaso) {
		String conteudo = enviar(clienteHttpIA,
				montarPedido(instrucoesTriagem, descricaoDoCaso, FormatoRespostaIA.classificacaoDeRisco()));
		return lerCor(conteudo);
	}

	public String resumirHistorico(String historico) {
		String resumo = enviar(clienteHttpIAResumo, montarPedido(instrucoesResumo, historico, null)).strip();
		if (resumo.isEmpty()) {
			throw respostaInvalida();
		}
		return resumo;
	}

	/** Pedido mínimo, só para o serviço de IA carregar o modelo antes do primeiro pedido de verdade. */
	public void aquecer() {
		enviar(clienteHttpIAResumo, montarPedido(INSTRUCOES_AQUECIMENTO, "ok", null));
	}

	private PedidoChatIA montarPedido(String instrucoes, String conteudo, FormatoRespostaIA formato) {
		return new PedidoChatIA(
				iaProperties.modelo(),
				List.of(MensagemChatIA.doSistema(instrucoes), MensagemChatIA.doUsuario(conteudo)),
				iaProperties.temperatura(),
				RACIOCINIO_DESLIGADO,
				formato);
	}

	private String enviar(RestClient cliente, PedidoChatIA pedido) {
		try {
			RespostaChatIA resposta = cliente.post()
					.uri(CAMINHO_CHAT)
					.body(pedido)
					.retrieve()
					.body(RespostaChatIA.class);
			return resposta == null ? "" : resposta.conteudo();
		} catch (RestClientException erro) {
			log.warn("Serviço de IA indisponível: {}", erro.getClass().getSimpleName());
			throw new IAIndisponivelException("O serviço de IA não respondeu. Siga sem a sugestão da IA.");
		}
	}

	private CorRisco lerCor(String conteudo) {
		String cor = lerCampoCor(conteudo);
		return Arrays.stream(CorRisco.values())
				.filter(corValida -> corValida.name().equals(cor))
				.findFirst()
				.orElseThrow(this::respostaInvalida);
	}

	private String lerCampoCor(String conteudo) {
		try {
			return leitorJson.readValue(conteudo, ClassificacaoIA.class).cor();
		} catch (JacksonException erro) {
			throw respostaInvalida();
		}
	}

	private IAIndisponivelException respostaInvalida() {
		log.warn("Serviço de IA devolveu uma resposta fora do formato esperado");
		return new IAIndisponivelException("A IA devolveu uma resposta inválida. Siga sem a sugestão da IA.");
	}
}
