package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriagemServiceTest {

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 10, 30);

	private static final String LOGIN_ENFERMEIRO = "enfermeira.teste";
	private static final SinaisVitaisDTO SINAIS_VITAIS = new SinaisVitaisDTO(
			"120/80", 88, 18, new BigDecimal("37.2"), new BigDecimal("97"));

	@Mock
	private TriagemRepository triagemRepository;

	@Mock
	private AtendimentoRepository atendimentoRepository;

	@Mock
	private UsuarioService usuarioService;

	@Spy
	private Clock clock = RelogioDeTeste.em(AGORA);

	@InjectMocks
	private TriagemService triagemService;

	@Test
	void triagemRegistradaAindaNaoTemClassificacaoOficial() {
		Atendimento atendimento = novoAtendimento(StatusAtendimento.ABERTO);
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));
		when(usuarioService.buscarPorLogin(LOGIN_ENFERMEIRO)).thenReturn(new Usuario());
		when(triagemRepository.save(any(Triagem.class))).thenAnswer(chamada -> chamada.getArgument(0));

		TriagemResponseDTO triagem = triagemService.registrar(
				new TriagemRequestDTO(atendimento.getId(), "Dor torácica", SINAIS_VITAIS), LOGIN_ENFERMEIRO);

		assertThat(triagem.corConfirmada()).isNull();
		assertThat(triagem.sinaisVitais()).isEqualTo(SINAIS_VITAIS);
		assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.EM_TRIAGEM);
	}

	@Test
	void atendimentoEncerradoNaoRecebeTriagem() {
		Atendimento atendimento = novoAtendimento(StatusAtendimento.ENCERRADO);
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));

		assertThatThrownBy(() -> triagemService.registrar(
				new TriagemRequestDTO(atendimento.getId(), "Dor torácica", SINAIS_VITAIS), LOGIN_ENFERMEIRO))
				.isInstanceOf(RegraNegocioException.class);
		verify(triagemRepository, never()).save(any());
	}

	@Test
	void atendimentoJaComOMedicoNaoVoltaParaTriagem() {
		Atendimento atendimento = novoAtendimento(StatusAtendimento.EM_ATENDIMENTO);
		when(atendimentoRepository.findById(atendimento.getId())).thenReturn(Optional.of(atendimento));

		assertThatThrownBy(() -> triagemService.registrar(
				new TriagemRequestDTO(atendimento.getId(), "Dor torácica", SINAIS_VITAIS), LOGIN_ENFERMEIRO))
				.isInstanceOf(RegraNegocioException.class);
		assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.EM_ATENDIMENTO);
	}

	@Test
	void enfermeiroConfirmaOuAlteraAClassificacao() {
		Triagem triagem = novaTriagem();
		triagem.setCorSugerida(CorRisco.AMARELO);
		Usuario enfermeiro = new Usuario();
		when(triagemRepository.findById(triagem.getId())).thenReturn(Optional.of(triagem));
		when(usuarioService.buscarPorLogin(LOGIN_ENFERMEIRO)).thenReturn(enfermeiro);

		triagemService.confirmarRisco(triagem.getId(), CorRisco.LARANJA, LOGIN_ENFERMEIRO);

		assertThat(triagem.getCorConfirmada()).isEqualTo(CorRisco.LARANJA);
		assertThat(triagem.getCorSugerida()).isEqualTo(CorRisco.AMARELO);
		assertThat(triagem.getConfirmador()).isSameAs(enfermeiro);
		assertThat(triagem.getConfirmadaEm()).isEqualTo(AGORA);
	}

	@Test
	void classificacaoJaConfirmadaNaoEhSobrescrita() {
		Triagem triagem = novaTriagem();
		triagem.setCorConfirmada(CorRisco.VERDE);
		when(triagemRepository.findById(triagem.getId())).thenReturn(Optional.of(triagem));

		assertThatThrownBy(() -> triagemService.confirmarRisco(triagem.getId(), CorRisco.AZUL, LOGIN_ENFERMEIRO))
				.isInstanceOf(RegraNegocioException.class);
		assertThat(triagem.getCorConfirmada()).isEqualTo(CorRisco.VERDE);
	}

	private Atendimento novoAtendimento(StatusAtendimento status) {
		Atendimento atendimento = new Atendimento();
		atendimento.setId(UUID.randomUUID());
		atendimento.setStatus(status);
		return atendimento;
	}

	private Triagem novaTriagem() {
		Triagem triagem = new Triagem();
		triagem.setId(UUID.randomUUID());
		triagem.setAtendimento(novoAtendimento(StatusAtendimento.EM_TRIAGEM));
		return triagem;
	}
}
