package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextoTriagemServiceTest {

	private static final String LOGIN_ENFERMEIRO = "enfermeira.teste";

	@Mock
	private TriagemRepository triagemRepository;

	@Mock
	private ConsentimentoRepository consentimentoRepository;

	@Mock
	private RegistroClinicoRepository registroClinicoRepository;

	@Mock
	private RegistroAuditoriaService registroAuditoriaService;

	private ContextoTriagemService contextoTriagemService;
	private Triagem triagem;
	private UUID pacienteId;

	@BeforeEach
	void prepararTriagem() {
		IAProperties configuracao = new IAProperties("http://ia.teste", "modelo-teste", null, 0,
				Duration.ofSeconds(5), 20, new ByteArrayResource(new byte[0]));
		contextoTriagemService = new ContextoTriagemService(triagemRepository, consentimentoRepository,
				registroClinicoRepository, registroAuditoriaService, configuracao);
		triagem = novaTriagem();
		pacienteId = triagem.getAtendimento().getProntuario().getPaciente().getId();
		when(triagemRepository.findById(triagem.getId())).thenReturn(Optional.of(triagem));
	}

	@Test
	void semConsentimentoUsaSoOsDadosDoMomentoESemAcessarOHistorico() {
		when(consentimentoRepository.findFirstByPacienteIdAndTipoOrderByVersaoDesc(pacienteId, TipoConsentimento.AMPLO))
				.thenReturn(Optional.of(consentimento(DecisaoConsentimento.NEGADO)));

		CasoTriagemDTO caso = contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO);

		assertThat(caso.registrosUsados()).isEmpty();
		assertThat(caso.descricao()).contains("Sintomas: Dor no peito").contains("Histórico autorizado: nenhum");
		verify(registroClinicoRepository, never()).buscarHistoricoSemRestricao(any(), any(), any());
		verify(registroAuditoriaService, never()).registrarAcesso(any());
	}

	@Test
	void comConsentimentoEnviaOHistoricoERegistraOAcessoNaAuditoria() {
		RegistroClinico alergia = registro(TipoRegistro.ALERGIA, "Alergia a dipirona");
		when(consentimentoRepository.findFirstByPacienteIdAndTipoOrderByVersaoDesc(pacienteId, TipoConsentimento.AMPLO))
				.thenReturn(Optional.of(consentimento(DecisaoConsentimento.AUTORIZADO)));
		when(registroClinicoRepository.buscarHistoricoSemRestricao(any(), any(), any())).thenReturn(List.of(alergia));

		CasoTriagemDTO caso = contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO);

		assertThat(caso.descricao()).contains("- ALERGIA: Alergia a dipirona");
		assertThat(caso.registrosUsados()).containsExactly(alergia.getId());
		ArgumentCaptor<AcessoProntuarioDTO> acesso = ArgumentCaptor.forClass(AcessoProntuarioDTO.class);
		verify(registroAuditoriaService).registrarAcesso(acesso.capture());
		assertThat(acesso.getValue().perfil()).isEqualTo(Perfil.ENFERMEIRO);
		assertThat(acesso.getValue().registrosAcessadosIds()).containsExactly(alergia.getId());
	}

	@Test
	void triagemJaConfirmadaNaoPedeNovaSugestao() {
		triagem.setCorConfirmada(CorRisco.AMARELO);

		assertThatThrownBy(() -> contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO))
				.isInstanceOf(RegraNegocioException.class);
	}

	private Triagem novaTriagem() {
		Paciente paciente = new Paciente();
		paciente.setId(UUID.randomUUID());
		Prontuario prontuario = new Prontuario();
		prontuario.setId(UUID.randomUUID());
		prontuario.setPaciente(paciente);
		Instituicao instituicao = new Instituicao();
		instituicao.setId(UUID.randomUUID());
		Atendimento atendimento = new Atendimento();
		atendimento.setProntuario(prontuario);
		atendimento.setInstituicao(instituicao);
		Triagem novaTriagem = new Triagem();
		novaTriagem.setId(UUID.randomUUID());
		novaTriagem.setAtendimento(atendimento);
		novaTriagem.setSintomas("Dor no peito");
		novaTriagem.setSinaisVitais(new SinaisVitais());
		return novaTriagem;
	}

	private Consentimento consentimento(DecisaoConsentimento decisao) {
		Consentimento consentimento = new Consentimento();
		consentimento.setDecisao(decisao);
		return consentimento;
	}

	private RegistroClinico registro(TipoRegistro tipo, String conteudo) {
		RegistroClinico registro = new RegistroClinico();
		registro.setId(UUID.randomUUID());
		registro.setTipo(tipo);
		registro.setConteudo(conteudo);
		return registro;
	}
}
