package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import com.sigh.sigh.project.ia.entity.ResumoHistoricoIA;
import com.sigh.sigh.project.ia.entity.StatusResumo;
import com.sigh.sigh.project.ia.repository.ResumoHistoricoIARepository;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.service.HistoricoAutorizadoService;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import com.sigh.sigh.project.usuario.service.VinculoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumoHistoricoServiceTest {

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 10, 30);

	private static final String LOGIN_MEDICO = "medico.teste";

	@Mock
	private ResumoHistoricoIARepository resumoHistoricoIARepository;

	@Mock
	private AtendimentoRepository atendimentoRepository;

	@Mock
	private HistoricoAutorizadoService historicoAutorizadoService;

	@Mock
	private RegistroAuditoriaService registroAuditoriaService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private VinculoService vinculoService;

	@Mock
	private FilaDeResumosIA filaDeResumos;

	private ResumoHistoricoService resumoHistoricoService;

	@BeforeEach
	public void prepararServico() {
		IAProperties configuracao = new IAProperties("http://ia.teste", "qwen3:8b", null, 0,
				Duration.ofSeconds(5), 20, new ByteArrayResource(new byte[0]), new ByteArrayResource(new byte[0]),
				Duration.ofSeconds(10), 5, false);
		resumoHistoricoService = new ResumoHistoricoService(resumoHistoricoIARepository, atendimentoRepository,
				historicoAutorizadoService, registroAuditoriaService, usuarioService, configuracao,
				RelogioDeTeste.em(AGORA), vinculoService, filaDeResumos);
	}

	@Test
	void semHistoricoAutorizadoNaoChamaAIA() {
		Atendimento atendimento = new Atendimento();
		atendimento.setId(UUID.randomUUID());
		atendimento.setInstituicao(new Instituicao());
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(historicoAutorizadoService.consultar(any(), any())).thenReturn(List.of());

		assertThatThrownBy(() -> resumoHistoricoService.solicitar(atendimento.getId(), LOGIN_MEDICO))
				.isInstanceOf(RegraNegocioException.class);
		verify(resumoHistoricoIARepository, never()).save(any());
	}

	@Test
	void resumoConcluidoGuardaOTextoEOModeloUsado() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.SOLICITADO);

		resumoHistoricoService.concluir(resumo.getId(), "Alergias: dipirona.");

		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.GERADO);
		assertThat(resumo.getTexto()).isEqualTo("Alergias: dipirona.");
		assertThat(resumo.getModeloVersao()).isEqualTo("qwen3:8b");
		assertThat(resumo.getGeradoEm()).isEqualTo(AGORA);
	}

	@Test
	void resumoQueJaFalhouNaoRecebeOTextoQueChegaDepois() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.FALHOU);

		resumoHistoricoService.concluir(resumo.getId(), "Alergias: dipirona.");

		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.FALHOU);
		assertThat(resumo.getTexto()).isNull();
	}

	@Test
	void continuaAutorizadoSoComVinculoEFontesVisiveis() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.SOLICITADO);
		Usuario solicitante = new Usuario();
		solicitante.setLogin(LOGIN_MEDICO);
		resumo.setSolicitante(solicitante);
		fontesVisiveis(resumo, true);
		assertThat(resumoHistoricoService.continuaAutorizado(resumo.getId())).isTrue();

		fontesVisiveis(resumo, false);
		assertThat(resumoHistoricoService.continuaAutorizado(resumo.getId())).as("fonte restrita na fila").isFalse();

		doThrow(new AccessDeniedException("sem vínculo")).when(vinculoService)
				.exigirVinculoAtivo(eq(LOGIN_MEDICO), any(), any());
		assertThat(resumoHistoricoService.continuaAutorizado(resumo.getId())).as("vínculo perdido na fila").isFalse();
	}

	@Test
	void consultaDeResumoEmGeracaoQueSaiuDaFilaMostraFalho() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.SOLICITADO);
		fontesVisiveis(resumo, true);
		when(filaDeResumos.pendente(resumo.getId())).thenReturn(true);
		assertThat(resumoHistoricoService.consultar(resumo.getId(), LOGIN_MEDICO).status()).isEqualTo(StatusResumo.SOLICITADO);

		when(filaDeResumos.pendente(resumo.getId())).thenReturn(false);
		assertThat(resumoHistoricoService.consultar(resumo.getId(), LOGIN_MEDICO).status()).isEqualTo(StatusResumo.FALHOU);
		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.FALHOU);
		verify(registroAuditoriaService, never()).registrarAcesso(any());
	}

	@Test
	void consultaPerguntaAFilaAntesDeLerOResumo() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.SOLICITADO);
		fontesVisiveis(resumo, true);

		resumoHistoricoService.consultar(resumo.getId(), LOGIN_MEDICO);

		InOrder ordem = inOrder(filaDeResumos, resumoHistoricoIARepository);
		ordem.verify(filaDeResumos).pendente(resumo.getId());
		ordem.verify(resumoHistoricoIARepository).findById(resumo.getId());
	}

	@Test
	void respostaDoPedidoNuncaTrazOTexto() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.GERADO);
		resumo.setTexto("Alergias: dipirona.");

		ResumoHistoricoResponseDTO resposta = resumoHistoricoService.emGeracao(resumo.getId());

		assertThat(resposta.status()).isEqualTo(StatusResumo.SOLICITADO);
		assertThat(resposta.texto()).isNull();
		verify(registroAuditoriaService, never()).registrarAcesso(any());
	}

	@Test
	void medicoValidaUmResumoGerado() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.GERADO);
		fontesVisiveis(resumo, true);
		Usuario medico = new Usuario();
		when(usuarioService.buscarPorLogin(LOGIN_MEDICO)).thenReturn(medico);

		resumoHistoricoService.validar(resumo.getId(), LOGIN_MEDICO);

		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.VALIDADO);
		assertThat(resumo.getValidador()).isSameAs(medico);
	}

	@Test
	void resumoQueFalhouNaoPodeSerValidado() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.FALHOU);
		fontesVisiveis(resumo, true);

		assertThatThrownBy(() -> resumoHistoricoService.validar(resumo.getId(), LOGIN_MEDICO))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("Só um resumo gerado");
		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.FALHOU);
	}

	@Test
	void resumoComFonteQueFicouRestritaFicaOcultoENaoEhValidado() {
		ResumoHistoricoIA resumo = resumoComStatus(StatusResumo.GERADO);
		resumo.setTexto("Episódio depressivo grave.");
		fontesVisiveis(resumo, false);

		ResumoHistoricoResponseDTO consultado = resumoHistoricoService.consultar(resumo.getId(), LOGIN_MEDICO);

		assertThat(consultado.texto()).isNull();
		assertThat(consultado.registrosDeOrigem()).isEmpty();
		assertThat(consultado.aviso()).contains("Resumo oculto");
		verify(registroAuditoriaService, never()).registrarAcesso(any());
		assertThatThrownBy(() -> resumoHistoricoService.validar(resumo.getId(), LOGIN_MEDICO))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("restrita");
		assertThat(resumo.getStatus()).isEqualTo(StatusResumo.GERADO);
	}

	private void fontesVisiveis(ResumoHistoricoIA resumo, boolean visiveis) {
		when(historicoAutorizadoService.continuamVisiveis(eq(resumo.getAtendimento()), any())).thenReturn(visiveis);
	}

	private ResumoHistoricoIA resumoComStatus(StatusResumo status) {
		Atendimento atendimento = new Atendimento();
		atendimento.setId(UUID.randomUUID());
		atendimento.setInstituicao(new Instituicao());
		ResumoHistoricoIA resumo = new ResumoHistoricoIA();
		resumo.setId(UUID.randomUUID());
		resumo.setAtendimento(atendimento);
		resumo.setStatus(status);
		when(resumoHistoricoIARepository.findById(resumo.getId())).thenReturn(Optional.of(resumo));
		return resumo;
	}
}
