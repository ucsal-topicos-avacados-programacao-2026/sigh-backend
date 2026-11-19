package com.sigh.sigh.project.auditoria.controller;

import com.sigh.sigh.project.auditoria.service.ConsultaAuditoriaService;
import com.sigh.sigh.project.config.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditoriaController.class)
@Import(MethodSecurityConfig.class)
class AuditoriaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ConsultaAuditoriaService consultaAuditoriaService;

	@Test
	@WithMockUser(username = "ana.teste", roles = "PACIENTE")
	void pacienteConsultaOsProprioAcessosPeloLogin() throws Exception {
		when(consultaAuditoriaService.consultarAcessosDoPaciente(eq("ana.teste"), any())).thenReturn(Page.empty());

		mockMvc.perform(get("/auditoria/meus-acessos")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "PACIENTE")
	void pacienteNaoConsultaOPainelDoAuditor() throws Exception {
		mockMvc.perform(get("/auditoria/registros").param("inicio", "2026-11-01").param("fim", "2026-11-19"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(consultaAuditoriaService);
	}

	@Test
	@WithMockUser(roles = "AUDITOR_GESTOR")
	void auditorConsultaRegistrosDoPeriodo() throws Exception {
		when(consultaAuditoriaService.consultarRegistros(any(), any())).thenReturn(Page.empty());

		mockMvc.perform(get("/auditoria/registros").param("inicio", "2026-11-01").param("fim", "2026-11-19"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "AUDITOR_GESTOR")
	void consultaSemPeriodoRetornaDadosInvalidos() throws Exception {
		mockMvc.perform(get("/auditoria/indicadores"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.inicio").exists());
		verifyNoInteractions(consultaAuditoriaService);
	}
}
