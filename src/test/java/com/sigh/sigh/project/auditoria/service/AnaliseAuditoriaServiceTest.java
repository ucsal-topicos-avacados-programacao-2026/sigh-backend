package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.AnaliseAuditoriaRequestDTO;
import com.sigh.sigh.project.auditoria.entity.AnaliseAuditoria;
import com.sigh.sigh.project.auditoria.entity.ConclusaoAnalise;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;
import com.sigh.sigh.project.auditoria.repository.AnaliseAuditoriaRepository;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.support.RelogioDeTeste;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliseAuditoriaServiceTest {

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 10, 30);

	private static final String LOGIN_AUDITOR = "auditor.teste";
	private static final AnaliseAuditoriaRequestDTO PARECER = new AnaliseAuditoriaRequestDTO(
			TipoAnalise.PARECER, ConclusaoAnalise.REGULAR, "Acesso compatível com o atendimento.");

	@Mock
	private AnaliseAuditoriaRepository analiseAuditoriaRepository;

	@Mock
	private RegistroAuditoriaRepository registroAuditoriaRepository;

	@Mock
	private UsuarioService usuarioService;

	@Spy
	private Clock clock = RelogioDeTeste.em(AGORA);

	@InjectMocks
	private AnaliseAuditoriaService analiseAuditoriaService;

	@Test
	void parecerFicaVinculadoAoRegistroEAoAuditorLogado() {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setId(UUID.randomUUID());
		Usuario auditor = new Usuario();
		when(registroAuditoriaRepository.findById(registro.getId())).thenReturn(Optional.of(registro));
		when(usuarioService.buscarPorLogin(LOGIN_AUDITOR)).thenReturn(auditor);
		when(analiseAuditoriaRepository.save(any(AnaliseAuditoria.class))).thenAnswer(chamada -> chamada.getArgument(0));

		analiseAuditoriaService.registrar(registro.getId(), PARECER, LOGIN_AUDITOR);

		ArgumentCaptor<AnaliseAuditoria> analise = ArgumentCaptor.forClass(AnaliseAuditoria.class);
		verify(analiseAuditoriaRepository).save(analise.capture());
		assertThat(analise.getValue().getRegistroAuditoria()).isSameAs(registro);
		assertThat(analise.getValue().getAuditor()).isSameAs(auditor);
		assertThat(analise.getValue().getRegistradaEm()).isEqualTo(AGORA);
	}

	@Test
	void registroDeAuditoriaInexistenteRetornaNaoEncontrado() {
		UUID registroId = UUID.randomUUID();
		when(registroAuditoriaRepository.findById(registroId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> analiseAuditoriaService.registrar(registroId, PARECER, LOGIN_AUDITOR))
				.isInstanceOf(RecursoNaoEncontradoException.class);
	}
}
