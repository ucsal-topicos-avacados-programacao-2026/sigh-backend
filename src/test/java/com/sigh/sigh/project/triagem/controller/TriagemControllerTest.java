package com.sigh.sigh.project.triagem.controller;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.config.MethodSecurityConfig;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.service.FilaAtendimentoService;
import com.sigh.sigh.project.triagem.service.SugestaoRiscoService;
import com.sigh.sigh.project.triagem.service.TriagemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TriagemController.class)
@Import(MethodSecurityConfig.class)
class TriagemControllerTest {

	private static final UUID TRIAGEM_ID = UUID.randomUUID();
	private static final String CONFIRMACAO = """
			{"cor": "LARANJA"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TriagemService triagemService;

	@MockitoBean
	private FilaAtendimentoService filaAtendimentoService;

	@MockitoBean
	private SugestaoRiscoService sugestaoRiscoService;

	@Test
	@WithMockUser(username = "enfermeira.teste", roles = "ENFERMEIRO")
	void iaForaDoArInformaAFalhaParaSeguirComAClassificacaoManual() throws Exception {
		when(sugestaoRiscoService.sugerirRisco(TRIAGEM_ID, "enfermeira.teste"))
				.thenThrow(new IAIndisponivelException("O serviço de IA não respondeu. Faça a classificação manual."));

		mockMvc.perform(post("/triagens/{id}/sugestao", TRIAGEM_ID).with(csrf()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("O serviço de IA não respondeu. Faça a classificação manual."));
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void medicoNaoPedeSugestaoDeRisco() throws Exception {
		mockMvc.perform(post("/triagens/{id}/sugestao", TRIAGEM_ID).with(csrf()))
				.andExpect(status().isForbidden());
		verifyNoInteractions(sugestaoRiscoService);
	}

	@Test
	@WithMockUser(username = "enfermeira.teste", roles = "ENFERMEIRO")
	void enfermeiroConfirmaComOProprioLogin() throws Exception {
		mockMvc.perform(post("/triagens/{id}/confirmacao", TRIAGEM_ID)
						.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACAO))
				.andExpect(status().isOk());
		verify(triagemService).confirmarRisco(TRIAGEM_ID, CorRisco.LARANJA, "enfermeira.teste");
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void medicoNaoConfirmaClassificacao() throws Exception {
		mockMvc.perform(post("/triagens/{id}/confirmacao", TRIAGEM_ID)
						.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACAO))
				.andExpect(status().isForbidden());
		verifyNoInteractions(triagemService);
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void medicoConsultaAFila() throws Exception {
		UUID instituicaoId = UUID.randomUUID();
		when(filaAtendimentoService.consultarFila(eq(instituicaoId), any(), any())).thenReturn(Page.empty());

		mockMvc.perform(get("/triagens/fila").param("instituicaoId", instituicaoId.toString()))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMINISTRATIVO")
	void administrativoNaoConsultaAFila() throws Exception {
		mockMvc.perform(get("/triagens/fila").param("instituicaoId", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
		verifyNoInteractions(filaAtendimentoService);
	}
}
