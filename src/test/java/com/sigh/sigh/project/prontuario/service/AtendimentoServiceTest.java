package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoRequestDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

	private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
	private static final String LOGIN_MEDICO = "medico.teste";
	private static final RegistroClinicoRequestDTO DIAGNOSTICO = new RegistroClinicoRequestDTO(
			TipoRegistro.DIAGNOSTICO, "Asma brônquica persistente leve.", "Clínica geral", "J45.9");

	@Mock
	private AtendimentoRepository atendimentoRepository;

	@Mock
	private RegistroClinicoRepository registroClinicoRepository;

	@Mock
	private UsuarioService usuarioService;

	@InjectMocks
	private AtendimentoService atendimentoService;

	@Test
	void registroFicaNoProntuarioComAutorEInstituicaoDoAtendimento() {
		Atendimento atendimento = atendimentoComStatus(StatusAtendimento.EM_TRIAGEM);
		Usuario medico = new Usuario();
		when(atendimentoRepository.findById(ATENDIMENTO_ID)).thenReturn(Optional.of(atendimento));
		when(usuarioService.buscarPorLogin(LOGIN_MEDICO)).thenReturn(medico);
		when(registroClinicoRepository.save(any(RegistroClinico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		atendimentoService.registrar(ATENDIMENTO_ID, DIAGNOSTICO, LOGIN_MEDICO);

		ArgumentCaptor<RegistroClinico> registroSalvo = ArgumentCaptor.forClass(RegistroClinico.class);
		verify(registroClinicoRepository).save(registroSalvo.capture());
		assertThat(registroSalvo.getValue().getProntuario()).isSameAs(atendimento.getProntuario());
		assertThat(registroSalvo.getValue().getOrigem()).isSameAs(atendimento.getInstituicao());
		assertThat(registroSalvo.getValue().getAutor()).isSameAs(medico);
		assertThat(registroSalvo.getValue().getCodigoCID10()).isEqualTo("J45.9");
	}

	@Test
	void primeiroRegistroIniciaOAtendimentoClinico() {
		Atendimento atendimento = atendimentoComStatus(StatusAtendimento.EM_TRIAGEM);
		Usuario medico = new Usuario();
		when(atendimentoRepository.findById(ATENDIMENTO_ID)).thenReturn(Optional.of(atendimento));
		when(usuarioService.buscarPorLogin(LOGIN_MEDICO)).thenReturn(medico);
		when(registroClinicoRepository.save(any(RegistroClinico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		atendimentoService.registrar(ATENDIMENTO_ID, DIAGNOSTICO, LOGIN_MEDICO);

		assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.EM_ATENDIMENTO);
		assertThat(atendimento.getMedicoResponsavel()).isSameAs(medico);
		assertThat(atendimento.getInicioClinicoEm()).isNotNull();
	}

	@Test
	void naoRegistraEmAtendimentoEncerrado() {
		when(atendimentoRepository.findById(ATENDIMENTO_ID))
				.thenReturn(Optional.of(atendimentoComStatus(StatusAtendimento.ENCERRADO)));

		assertThatThrownBy(() -> atendimentoService.registrar(ATENDIMENTO_ID, DIAGNOSTICO, LOGIN_MEDICO))
				.isInstanceOf(RegraNegocioException.class);
		verify(registroClinicoRepository, never()).save(any());
	}

	@Test
	void encerraAtendimentoEmAndamento() {
		Atendimento atendimento = atendimentoComStatus(StatusAtendimento.EM_ATENDIMENTO);
		when(atendimentoRepository.findById(ATENDIMENTO_ID)).thenReturn(Optional.of(atendimento));

		atendimentoService.encerrar(ATENDIMENTO_ID);

		assertThat(atendimento.getStatus()).isEqualTo(StatusAtendimento.ENCERRADO);
	}

	@Test
	void naoEncerraAtendimentoJaEncerrado() {
		when(atendimentoRepository.findById(ATENDIMENTO_ID))
				.thenReturn(Optional.of(atendimentoComStatus(StatusAtendimento.ENCERRADO)));

		assertThatThrownBy(() -> atendimentoService.encerrar(ATENDIMENTO_ID))
				.isInstanceOf(RegraNegocioException.class);
	}

	private Atendimento atendimentoComStatus(StatusAtendimento status) {
		Atendimento atendimento = new Atendimento();
		atendimento.setId(ATENDIMENTO_ID);
		atendimento.setProntuario(new Prontuario());
		atendimento.setInstituicao(new Instituicao());
		atendimento.setStatus(status);
		return atendimento;
	}
}
