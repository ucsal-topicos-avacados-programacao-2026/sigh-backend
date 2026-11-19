package com.sigh.sigh.project.prontuario.controller;

import com.sigh.sigh.project.config.MethodSecurityConfig;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoRequestDTO;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoResponseDTO;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.service.AtendimentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AtendimentoController.class)
@Import(MethodSecurityConfig.class)
class AtendimentoControllerTest {

	private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
	private static final String URL_REGISTROS = "/atendimentos/" + ATENDIMENTO_ID + "/registros";
	private static final String DIAGNOSTICO = """
			{"tipo": "DIAGNOSTICO", "conteudo": "Asma brônquica persistente leve.",
			"categoriaInformacao": "Clínica geral", "codigoCID10": "J45.9"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AtendimentoService atendimentoService;

	@Test
	@WithMockUser(username = "medico.teste", roles = "MEDICO")
	void medicoRegistraDiagnostico() throws Exception {
		when(atendimentoService.registrar(eq(ATENDIMENTO_ID), any(RegistroClinicoRequestDTO.class), eq("medico.teste")))
				.thenReturn(new RegistroClinicoResponseDTO(UUID.randomUUID(), ATENDIMENTO_ID, TipoRegistro.DIAGNOSTICO,
						LocalDateTime.now(), "Asma brônquica persistente leve.", "Clínica geral", "J45.9"));

		mockMvc.perform(post(URL_REGISTROS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DIAGNOSTICO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigoCID10").value("J45.9"));
	}

	@Test
	@WithMockUser(roles = "ENFERMEIRO")
	void outroPerfilNaoRegistra() throws Exception {
		mockMvc.perform(post(URL_REGISTROS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DIAGNOSTICO))
				.andExpect(status().isForbidden());
		verifyNoInteractions(atendimentoService);
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void cidForaDoFormatoRetornaDadosInvalidos() throws Exception {
		String cidInvalido = """
				{"tipo": "DIAGNOSTICO", "conteudo": "Asma.", "categoriaInformacao": "Clínica geral", "codigoCID10": "asma"}
				""";

		mockMvc.perform(post(URL_REGISTROS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cidInvalido))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.codigoCID10").exists());
		verifyNoInteractions(atendimentoService);
	}

	@Test
	@WithMockUser(roles = "MEDICO")
	void medicoEncerraAtendimento() throws Exception {
		mockMvc.perform(post("/atendimentos/" + ATENDIMENTO_ID + "/encerramento").with(csrf()))
				.andExpect(status().isNoContent());
	}
}
