package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.service.ConsentimentoService;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricoAutorizadoServiceTest {

	private static final int LIMITE_DE_REGISTROS = 20;
	private static final ConsultaHistoricoDTO CONSULTA_DO_MEDICO =
			new ConsultaHistoricoDTO("medico.teste", Perfil.MEDICO, "Resumo do histórico por IA", LIMITE_DE_REGISTROS);

	@Mock
	private ConsentimentoService consentimentoService;

	@Mock
	private RegistroClinicoRepository registroClinicoRepository;

	@Mock
	private RegistroAuditoriaService registroAuditoriaService;

	private HistoricoAutorizadoService historicoAutorizadoService;
	private Atendimento atendimento;
	private UUID pacienteId;

	@BeforeEach
	public void prepararAtendimento() {
		historicoAutorizadoService = new HistoricoAutorizadoService(consentimentoService, registroClinicoRepository,
				registroAuditoriaService, RelogioDeTeste.em(LocalDateTime.of(2026, 11, 19, 10, 30)));
		Paciente paciente = new Paciente();
		paciente.setId(UUID.randomUUID());
		pacienteId = paciente.getId();
		Prontuario prontuario = new Prontuario();
		prontuario.setId(UUID.randomUUID());
		prontuario.setPaciente(paciente);
		Instituicao instituicao = new Instituicao();
		instituicao.setId(UUID.randomUUID());
		atendimento = new Atendimento();
		atendimento.setProntuario(prontuario);
		atendimento.setInstituicao(instituicao);
	}

	@Test
	void consentimentoQueNaoPermiteCompartilharLimitaAosRegistrosDaPropriaInstituicaoSemGerarAcessoVazio() {
		compartilhamentoPermitido(false);

		List<RegistroClinico> historico = historicoAutorizadoService.consultar(atendimento, CONSULTA_DO_MEDICO);

		assertThat(historico).isEmpty();
		verify(consentimentoService).podeCompartilharCom(pacienteId, atendimento.getInstituicao().getId());
		verify(registroClinicoRepository).historicoVisivel(eq(atendimento.getProntuario().getId()),
				eq(atendimento.getInstituicao().getId()), eq(false), any(), eq(Limit.of(LIMITE_DE_REGISTROS)));
		verify(registroAuditoriaService, never()).registrarAcesso(any());
	}

	@Test
	void consentimentoQuePermiteCompartilharTrazHistoricoLimitadoERegistraOAcesso() {
		RegistroClinico registro = new RegistroClinico();
		registro.setId(UUID.randomUUID());
		compartilhamentoPermitido(true);
		when(registroClinicoRepository.historicoVisivel(eq(atendimento.getProntuario().getId()),
				eq(atendimento.getInstituicao().getId()), eq(true), any(), eq(Limit.of(LIMITE_DE_REGISTROS))))
				.thenReturn(List.of(registro));

		List<RegistroClinico> historico = historicoAutorizadoService.consultar(atendimento, CONSULTA_DO_MEDICO);

		assertThat(historico).containsExactly(registro);
		ArgumentCaptor<AcessoProntuarioDTO> acesso = ArgumentCaptor.forClass(AcessoProntuarioDTO.class);
		verify(registroAuditoriaService).registrarAcesso(acesso.capture());
		assertThat(acesso.getValue().perfil()).isEqualTo(Perfil.MEDICO);
		assertThat(acesso.getValue().finalidade()).isEqualTo("Resumo do histórico por IA");
		assertThat(acesso.getValue().registrosAcessadosIds()).containsExactly(registro.getId());
		assertThat(acesso.getValue().emergencia()).isFalse();
	}

	@Test
	void acessoDeEmergenciaRegistraAJustificativaETrazOProntuarioInteiro() {
		RegistroClinico restrito = new RegistroClinico();
		restrito.setId(UUID.randomUUID());
		when(registroClinicoRepository.historicoCompleto(atendimento.getProntuario().getId())).thenReturn(List.of(restrito));

		List<RegistroClinico> historico = historicoAutorizadoService.consultarEmEmergencia(atendimento,
				CONSULTA_DO_MEDICO, "Paciente inconsciente, risco iminente de morte");

		assertThat(historico).containsExactly(restrito);
		ArgumentCaptor<AcessoProntuarioDTO> acesso = ArgumentCaptor.forClass(AcessoProntuarioDTO.class);
		verify(registroAuditoriaService).registrarAcesso(acesso.capture());
		assertThat(acesso.getValue().emergencia()).isTrue();
		assertThat(acesso.getValue().justificativa()).isEqualTo("Paciente inconsciente, risco iminente de morte");
		assertThat(acesso.getValue().registrosAcessadosIds()).containsExactly(restrito.getId());
		verify(consentimentoService, never()).podeCompartilharCom(any(), any());
	}

	@Test
	void acessoDeEmergenciaComProntuarioVazioTambemFicaNaAuditoria() {
		when(registroClinicoRepository.historicoCompleto(atendimento.getProntuario().getId())).thenReturn(List.of());

		historicoAutorizadoService.consultarEmEmergencia(atendimento, CONSULTA_DO_MEDICO, "Parada cardiorrespiratória em curso");

		verify(registroAuditoriaService).registrarAcesso(any());
	}

	private void compartilhamentoPermitido(boolean permitido) {
		when(consentimentoService.podeCompartilharCom(pacienteId, atendimento.getInstituicao().getId()))
				.thenReturn(permitido);
	}
}
