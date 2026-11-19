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

	private final RestClient clienteHttpIA;
	private final IAProperties iaProperties;
	private final JsonMapper leitorJson;
	private final String instrucoesTriagem;

	public ClienteIA(RestClient clienteHttpIA, IAProperties iaProperties, JsonMapper leitorJson) throws IOException {
		this.clienteHttpIA = clienteHttpIA;
		this.iaProperties = iaProperties;
		this.leitorJson = leitorJson;
		this.instrucoesTriagem = iaProperties.instrucoesTriagem().getContentAsString(StandardCharsets.UTF_8);
	}

	public CorRisco classificarRisco(String descricaoDoCaso) {
		PedidoChatIA pedido = new PedidoChatIA(
				iaProperties.modelo(),
				List.of(MensagemChatIA.doSistema(instrucoesTriagem), MensagemChatIA.doUsuario(descricaoDoCaso)),
				iaProperties.temperatura(),
				RACIOCINIO_DESLIGADO,
				FormatoRespostaIA.classificacaoDeRisco());
		return lerCor(enviar(pedido));
	}

	private String enviar(PedidoChatIA pedido) {
		try {
			RespostaChatIA resposta = clienteHttpIA.post()
					.uri(CAMINHO_CHAT)
					.body(pedido)
					.retrieve()
					.body(RespostaChatIA.class);
			return resposta == null ? "" : resposta.conteudo();
		} catch (RestClientException erro) {
			log.warn("Serviço de IA indisponível: {}", erro.getClass().getSimpleName());
			throw new IAIndisponivelException("O serviço de IA não respondeu. Faça a classificação manual.");
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
		log.warn("Serviço de IA devolveu uma classificação fora das cores do protocolo");
		return new IAIndisponivelException("A IA devolveu uma classificação inválida. Faça a classificação manual.");
	}
}
