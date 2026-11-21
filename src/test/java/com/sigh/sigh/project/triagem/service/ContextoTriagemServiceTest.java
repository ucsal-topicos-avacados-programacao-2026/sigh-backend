package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.service.HistoricoAutorizadoService;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import com.sigh.sigh.project.triagem.entity.Triagem;
import com.sigh.sigh.project.triagem.repository.TriagemRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextoTriagemServiceTest {

	private static final String LOGIN_ENFERMEIRO = "enfermeira.teste";

	@Mock
	private TriagemRepository triagemRepository;

	@Mock
	private HistoricoAutorizadoService historicoAutorizadoService;

	private ContextoTriagemService contextoTriagemService;

	private Triagem triagem;

	@Mock
	private VinculoService vinculoService;

	@BeforeEach
	public void prepararTriagem() {
		IAProperties configuracao = new IAProperties("http://ia.teste", "modelo-teste", null, 0,
				Duration.ofSeconds(5), 20, new ByteArrayResource(new byte[0]), new ByteArrayResource(new byte[0]),
				Duration.ofSeconds(10), 5, false);
		contextoTriagemService = new ContextoTriagemService(triagemRepository, historicoAutorizadoService, configuracao,
				vinculoService);
		triagem = new Triagem();
		triagem.setId(UUID.randomUUID());
		Atendimento atendimento = new Atendimento();
		atendimento.setInstituicao(new Instituicao());
		triagem.setAtendimento(atendimento);
		triagem.setSintomas("Dor no peito");
		triagem.setSinaisVitais(new SinaisVitais());
		when(triagemRepository.findById(triagem.getId())).thenReturn(Optional.of(triagem));
	}

	@Test
	void semHistoricoAutorizadoUsaSoOsDadosDoMomento() {
		when(historicoAutorizadoService.consultar(eq(triagem.getAtendimento()), any())).thenReturn(List.of());

		CasoTriagemDTO caso = contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO);

		assertThat(caso.registrosUsados()).isEmpty();
		assertThat(caso.descricao()).contains("Sintomas: Dor no peito").contains("Histórico autorizado: nenhum");
	}

	@Test
	void historicoAutorizadoEntraNaDescricaoEConsultadoComoEnfermeiro() {
		RegistroClinico alergia = registro("Alergia a dipirona");
		when(historicoAutorizadoService.consultar(eq(triagem.getAtendimento()), any())).thenReturn(List.of(alergia));

		CasoTriagemDTO caso = contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO);

		assertThat(caso.descricao()).contains("ALERGIA: Alergia a dipirona");
		assertThat(caso.registrosUsados()).containsExactly(alergia.getId());
		ArgumentCaptor<ConsultaHistoricoDTO> consulta = ArgumentCaptor.forClass(ConsultaHistoricoDTO.class);
		verify(historicoAutorizadoService).consultar(eq(triagem.getAtendimento()), consulta.capture());
		assertThat(consulta.getValue().perfil()).isEqualTo(Perfil.ENFERMEIRO);
		assertThat(consulta.getValue().loginProfissional()).isEqualTo(LOGIN_ENFERMEIRO);
	}

	@Test
	void triagemJaConfirmadaNaoPedeNovaSugestao() {
		triagem.setCorConfirmada(CorRisco.AMARELO);

		assertThatThrownBy(() -> contextoTriagemService.montarCaso(triagem.getId(), LOGIN_ENFERMEIRO))
				.isInstanceOf(RegraNegocioException.class);
	}

	private RegistroClinico registro(String conteudo) {
		Instituicao origem = new Instituicao();
		origem.setNome("Hospital Fictício");
		RegistroClinico registro = new RegistroClinico();
		registro.setId(UUID.randomUUID());
		registro.setTipo(TipoRegistro.ALERGIA);
		registro.setConteudo(conteudo);
		registro.setCriadoEm(LocalDateTime.of(2026, 9, 20, 10, 0));
		registro.setOrigem(origem);
		return registro;
	}
}
