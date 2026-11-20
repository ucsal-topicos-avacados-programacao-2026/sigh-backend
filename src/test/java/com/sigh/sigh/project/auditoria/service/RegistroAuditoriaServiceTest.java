package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroAuditoriaServiceTest {

	private static final String LOGIN_MEDICO = "medico.teste";

	@Mock
	private RegistroAuditoriaRepository registroAuditoriaRepository;

	@Mock
	private ProntuarioRepository prontuarioRepository;

	@Mock
	private InstituicaoRepository instituicaoRepository;

	@Mock
	private RegistroClinicoRepository registroClinicoRepository;

	@Mock
	private UsuarioService usuarioService;

	@InjectMocks
	private RegistroAuditoriaService registroAuditoriaService;

	@Test
	void registraQuemAcessouOQueEComQualFinalidade() {
		UUID registroClinicoId = UUID.randomUUID();
		Usuario medico = new Usuario();
		RegistroClinico registroClinico = new RegistroClinico();
		when(usuarioService.buscarPorLogin(LOGIN_MEDICO)).thenReturn(medico);
		when(registroClinicoRepository.getReferenceById(registroClinicoId)).thenReturn(registroClinico);

		registroAuditoriaService.registrarAcesso(acesso(false, null, Set.of(registroClinicoId)));

		ArgumentCaptor<RegistroAuditoria> registro = ArgumentCaptor.forClass(RegistroAuditoria.class);
		verify(registroAuditoriaRepository).save(registro.capture());
		assertThat(registro.getValue().getAtor()).isSameAs(medico);
		assertThat(registro.getValue().getPerfil()).isEqualTo(Perfil.MEDICO);
		assertThat(registro.getValue().getOcorridoEm()).isNotNull();
		assertThat(registro.getValue().getRegistrosAcessados()).containsExactly(registroClinico);
	}

	@Test
	void acessoDeEmergenciaSemJustificativaNaoEhRegistrado() {
		assertThatThrownBy(() -> registroAuditoriaService.registrarAcesso(acesso(true, " ", Set.of())))
				.isInstanceOf(RegraNegocioException.class);
		verify(registroAuditoriaRepository, never()).save(any());
	}

	private AcessoProntuarioDTO acesso(boolean emergencia, String justificativa, Set<UUID> registrosAcessados) {
		return new AcessoProntuarioDTO(UUID.randomUUID(), LOGIN_MEDICO, Perfil.MEDICO, UUID.randomUUID(),
				"Consulta ao histórico", emergencia, justificativa, registrosAcessados);
	}
}
