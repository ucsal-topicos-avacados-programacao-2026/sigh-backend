package com.sigh.sigh.project.leito.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.leito.dto.InternacaoRequestDTO;
import com.sigh.sigh.project.leito.entity.Internacao;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.MotivoOcupacao;
import com.sigh.sigh.project.leito.entity.OcupacaoLeito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import com.sigh.sigh.project.leito.repository.InternacaoRepository;
import com.sigh.sigh.project.leito.repository.LeitoRepository;
import com.sigh.sigh.project.leito.repository.OcupacaoLeitoRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternacaoServiceTest {

	private static final Instituicao HOSPITAL = hospital();

	private static final String LOGIN_ADMINISTRATIVO = "administrativo.teste";

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 10, 30);

	@Mock
	private InternacaoRepository internacaoRepository;

	@Mock
	private OcupacaoLeitoRepository ocupacaoLeitoRepository;

	@Mock
	private LeitoRepository leitoRepository;

	@Mock
	private AtendimentoRepository atendimentoRepository;

	@Mock
	private RegistroClinicoRepository registroClinicoRepository;

	@Spy
	private Clock clock = RelogioDeTeste.em(AGORA);

	@Mock
	private VinculoService vinculoService;

	@InjectMocks
	private InternacaoService internacaoService;

	@Test
	void internarEmLeitoLivreOcupaOLeitoEVinculaAoProntuario() {
		Atendimento atendimento = novoAtendimento();
		Leito leito = novoLeito(StatusLeito.LIVRE);
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		internacaoService.internar(new InternacaoRequestDTO(atendimento.getId(), leito.getId()), LOGIN_ADMINISTRATIVO);

		ArgumentCaptor<Internacao> internacao = ArgumentCaptor.forClass(Internacao.class);
		verify(internacaoRepository).save(internacao.capture());
		assertThat(internacao.getValue().getProntuario()).isSameAs(atendimento.getProntuario());
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.OCUPADO);
		ArgumentCaptor<OcupacaoLeito> ocupacao = ArgumentCaptor.forClass(OcupacaoLeito.class);
		verify(ocupacaoLeitoRepository).save(ocupacao.capture());
		assertThat(ocupacao.getValue().getMotivoInicio()).isEqualTo(MotivoOcupacao.INTERNACAO);
	}

	@Test
	void internarEmLeitoNaoLivreEhBloqueado() {
		Atendimento atendimento = novoAtendimento();
		Leito leito = novoLeito(StatusLeito.EM_HIGIENIZACAO);
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		assertThatThrownBy(() -> internacaoService.internar(new InternacaoRequestDTO(atendimento.getId(), leito.getId()), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
		verify(internacaoRepository, never()).save(any());
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.EM_HIGIENIZACAO);
	}

	@Test
	void internarPacienteJaInternadoEhBloqueado() {
		Atendimento atendimento = novoAtendimento();
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(internacaoRepository.existsByProntuarioIdAndAltaEmIsNull(atendimento.getProntuario().getId()))
				.thenReturn(true);

		assertThatThrownBy(() -> internacaoService.internar(new InternacaoRequestDTO(atendimento.getId(), UUID.randomUUID()), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
		verify(internacaoRepository, never()).save(any());
	}

	@Test
	void transferirOcupaODestinoEColocaAOrigemEmHigienizacao() {
		Internacao internacao = novaInternacao();
		Leito origem = novoLeito(StatusLeito.OCUPADO);
		Leito destino = novoLeito(StatusLeito.LIVRE);
		OcupacaoLeito ocupacaoAtual = novaOcupacao(origem, internacao);
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(leitoRepository.findComBloqueioById(destino.getId())).thenReturn(Optional.of(destino));
		when(ocupacaoLeitoRepository.buscarOcupacaoAtual(internacao.getId())).thenReturn(Optional.of(ocupacaoAtual));

		internacaoService.transferir(internacao.getId(), destino.getId(), LOGIN_ADMINISTRATIVO);

		assertThat(origem.getStatus()).isEqualTo(StatusLeito.EM_HIGIENIZACAO);
		assertThat(ocupacaoAtual.getFimEm()).isEqualTo(AGORA);
		assertThat(destino.getStatus()).isEqualTo(StatusLeito.OCUPADO);
	}

	@Test
	void transferirParaLeitoNaoLivreEhBloqueadoEMantemAOrigem() {
		Internacao internacao = novaInternacao();
		Leito destino = novoLeito(StatusLeito.BLOQUEADO);
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(leitoRepository.findComBloqueioById(destino.getId())).thenReturn(Optional.of(destino));

		assertThatThrownBy(() -> internacaoService.transferir(internacao.getId(), destino.getId(), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
		verify(ocupacaoLeitoRepository, never()).buscarOcupacaoAtual(any());
	}

	@Test
	void darAltaAnexaOResumoEColocaOLeitoEmHigienizacao() {
		Internacao internacao = novaInternacao();
		Leito leito = novoLeito(StatusLeito.OCUPADO);
		RegistroClinico resumo = novoRegistro(TipoRegistro.RESUMO_ALTA, internacao.getProntuario());
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(registroClinicoRepository.findById(resumo.getId())).thenReturn(Optional.of(resumo));
		when(ocupacaoLeitoRepository.buscarOcupacaoAtual(internacao.getId()))
				.thenReturn(Optional.of(novaOcupacao(leito, internacao)));

		internacaoService.darAlta(internacao.getId(), resumo.getId(), LOGIN_ADMINISTRATIVO);

		assertThat(internacao.getAltaEm()).isEqualTo(AGORA);
		assertThat(internacao.getResumoAlta()).isSameAs(resumo);
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.EM_HIGIENIZACAO);
	}

	@Test
	void darAltaComRegistroQueNaoEhResumoDeAltaEhBloqueado() {
		Internacao internacao = novaInternacao();
		RegistroClinico evolucao = novoRegistro(TipoRegistro.EVOLUCAO, internacao.getProntuario());
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(registroClinicoRepository.findById(evolucao.getId())).thenReturn(Optional.of(evolucao));

		assertThatThrownBy(() -> internacaoService.darAlta(internacao.getId(), evolucao.getId(), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
		assertThat(internacao.getAltaEm()).isNull();
	}

	@Test
	void darAltaComResumoDeOutroProntuarioEhBloqueado() {
		Internacao internacao = novaInternacao();
		RegistroClinico resumo = novoRegistro(TipoRegistro.RESUMO_ALTA, novoProntuario());
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(registroClinicoRepository.findById(resumo.getId())).thenReturn(Optional.of(resumo));

		assertThatThrownBy(() -> internacaoService.darAlta(internacao.getId(), resumo.getId(), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void internacaoJaEncerradaNaoAceitaTransferencia() {
		Internacao internacao = novaInternacao();
		internacao.setAltaEm(LocalDateTime.now(clock));
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));

		assertThatThrownBy(() -> internacaoService.transferir(internacao.getId(), UUID.randomUUID(), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class);
	}

	private static Instituicao hospital() {
		Instituicao instituicao = new Instituicao();
		instituicao.setId(UUID.randomUUID());
		return instituicao;
	}

	@Test
	void internarEmLeitoDeOutraInstituicaoEhBloqueado() {
		Atendimento atendimento = novoAtendimento();
		Leito leito = novoLeito(StatusLeito.LIVRE);
		leito.setInstituicao(hospital());
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(leitoRepository.findComBloqueioById(leito.getId())).thenReturn(Optional.of(leito));

		assertThatThrownBy(() -> internacaoService.internar(new InternacaoRequestDTO(atendimento.getId(), leito.getId()),
				LOGIN_ADMINISTRATIVO))
				.isInstanceOf(RegraNegocioException.class)
				.hasMessageContaining("outra instituição");
		verify(internacaoRepository, never()).save(any());
		assertThat(leito.getStatus()).isEqualTo(StatusLeito.LIVRE);
	}

	@Test
	void semVinculoComAInstituicaoDoAtendimentoNaoInterna() {
		Atendimento atendimento = novoAtendimento();
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		doThrow(new AccessDeniedException("sem vínculo")).when(vinculoService)
				.exigirVinculoAtivo(LOGIN_ADMINISTRATIVO, HOSPITAL.getId(), Perfil.ADMINISTRATIVO);

		assertThatThrownBy(() -> internacaoService.internar(new InternacaoRequestDTO(atendimento.getId(), UUID.randomUUID()),
				LOGIN_ADMINISTRATIVO))
				.isInstanceOf(AccessDeniedException.class);
		verify(internacaoRepository, never()).save(any());
		verify(leitoRepository, never()).findComBloqueioById(any());
	}

	@Test
	void transferenciaExigeVinculoComAInstituicaoOndeOPacienteEstaInternado() {
		Internacao internacao = novaInternacao();
		UUID instituicaoAtual = UUID.randomUUID();
		when(internacaoRepository.findById(internacao.getId())).thenReturn(Optional.of(internacao));
		when(ocupacaoLeitoRepository.instituicaoDaOcupacaoAtual(internacao.getId())).thenReturn(Optional.of(instituicaoAtual));
		doThrow(new AccessDeniedException("sem vínculo")).when(vinculoService)
				.exigirVinculoAtivo(LOGIN_ADMINISTRATIVO, instituicaoAtual, Perfil.ADMINISTRATIVO);

		assertThatThrownBy(() -> internacaoService.transferir(internacao.getId(), UUID.randomUUID(), LOGIN_ADMINISTRATIVO))
				.isInstanceOf(AccessDeniedException.class);
		verify(ocupacaoLeitoRepository, never()).buscarOcupacaoAtual(any());
	}

	private Prontuario novoProntuario() {
		Prontuario prontuario = new Prontuario();
		prontuario.setId(UUID.randomUUID());
		return prontuario;
	}

	private Atendimento novoAtendimento() {
		Atendimento atendimento = new Atendimento();
		atendimento.setId(UUID.randomUUID());
		atendimento.setProntuario(novoProntuario());
		atendimento.setInstituicao(HOSPITAL);
		return atendimento;
	}

	private Internacao novaInternacao() {
		Internacao internacao = new Internacao();
		internacao.setId(UUID.randomUUID());
		internacao.setProntuario(novoProntuario());
		internacao.setAtendimentoOrigem(novoAtendimento());
		return internacao;
	}

	private Leito novoLeito(StatusLeito status) {
		Leito leito = new Leito();
		leito.setId(UUID.randomUUID());
		leito.setStatus(status);
		leito.setInstituicao(HOSPITAL);
		return leito;
	}

	private OcupacaoLeito novaOcupacao(Leito leito, Internacao internacao) {
		OcupacaoLeito ocupacao = new OcupacaoLeito();
		ocupacao.setLeito(leito);
		ocupacao.setInternacao(internacao);
		return ocupacao;
	}

	private RegistroClinico novoRegistro(TipoRegistro tipo, Prontuario prontuario) {
		RegistroClinico registro = new RegistroClinico();
		registro.setId(UUID.randomUUID());
		registro.setTipo(tipo);
		registro.setProntuario(prontuario);
		return registro;
	}
}
