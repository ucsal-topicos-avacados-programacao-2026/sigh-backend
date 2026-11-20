package com.sigh.sigh.project.leito.controller;

import com.sigh.sigh.project.config.MethodSecurityConfig;
import com.sigh.sigh.project.leito.dto.LeitoRequestDTO;
import com.sigh.sigh.project.leito.dto.LeitoResponseDTO;
import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import com.sigh.sigh.project.leito.service.LeitoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeitoController.class)
@Import(MethodSecurityConfig.class)
class LeitoControllerTest {

	private static final UUID INSTITUICAO_ID = UUID.randomUUID();
	private static final String LEITO_VALIDO = """
			{"instituicaoId": "%s", "codigo": "UTI-01", "setor": "UTI adulto", "categoria": "UTI"}
			""".formatted(INSTITUICAO_ID);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LeitoService leitoService;

	@Test
	@WithMockUser(roles = "ADMINISTRATIVO")
	void administrativoCadastraLeito() throws Exception {
		when(leitoService.cadastrar(any(LeitoRequestDTO.class))).thenReturn(new LeitoResponseDTO(
				UUID.randomUUID(), INSTITUICAO_ID, "UTI-01", "UTI adulto", CategoriaLeito.UTI, StatusLeito.LIVRE));

		mockMvc.perform(post("/leitos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LEITO_VALIDO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("LIVRE"));
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void outroPerfilNaoCadastraLeito() throws Exception {
		mockMvc.perform(post("/leitos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LEITO_VALIDO))
				.andExpect(status().isForbidden());
		verifyNoInteractions(leitoService);
	}

	@Test
	@WithMockUser(roles = "ADMINISTRATIVO")
	void leitoSemCategoriaRetornaDadosInvalidos() throws Exception {
		String semCategoria = """
				{"instituicaoId": "%s", "codigo": "UTI-01", "setor": "UTI adulto"}
				""".formatted(INSTITUICAO_ID);

		mockMvc.perform(post("/leitos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(semCategoria))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.categoria").exists());
		verifyNoInteractions(leitoService);
	}
}
