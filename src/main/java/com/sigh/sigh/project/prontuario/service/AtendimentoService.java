package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoRequestDTO;
import com.sigh.sigh.project.prontuario.dto.RegistroClinicoResponseDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtendimentoService {

	private final AtendimentoRepository atendimentoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;
	private final VinculoService vinculoService;

	@Transactional
	public RegistroClinicoResponseDTO registrar(UUID atendimentoId, RegistroClinicoRequestDTO dados, String loginMedico) {
		Atendimento atendimento = buscarAtendimentoEmAndamento(atendimentoId, loginMedico);
		Usuario medico = usuarioService.buscarPorLogin(loginMedico);
		iniciarAtendimentoClinico(atendimento, medico);
		RegistroClinico registro = new RegistroClinico();
		registro.setProntuario(atendimento.getProntuario());
		registro.setAtendimento(atendimento);
		registro.setTipo(dados.tipo());
		registro.setCriadoEm(LocalDateTime.now(clock));
		registro.setConteudo(dados.conteudo());
		registro.setCategoriaInformacao(dados.categoriaInformacao());
		registro.setCodigoCID10(dados.codigoCID10());
		registro.setAutor(medico);
		registro.setOrigem(atendimento.getInstituicao());
		return RegistroClinicoResponseDTO.de(registroClinicoRepository.save(registro));
	}

	@Transactional
	public void encerrar(UUID atendimentoId, String loginMedico) {
		buscarAtendimentoEmAndamento(atendimentoId, loginMedico).setStatus(StatusAtendimento.ENCERRADO);
	}

	private Atendimento buscarAtendimentoEmAndamento(UUID atendimentoId, String loginMedico) {
		Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		vinculoService.exigirVinculoAtivo(loginMedico, atendimento.getInstituicao().getId(), Perfil.MEDICO);
		if (atendimento.getStatus() == StatusAtendimento.ENCERRADO) {
			throw new RegraNegocioException("Este atendimento já foi encerrado.");
		}
		return atendimento;
	}

	private void iniciarAtendimentoClinico(Atendimento atendimento, Usuario medico) {
		if (atendimento.getInicioClinicoEm() != null) {
			return;
		}
		atendimento.setInicioClinicoEm(LocalDateTime.now(clock));
		atendimento.setMedicoResponsavel(medico);
		atendimento.setStatus(StatusAtendimento.EM_ATENDIMENTO);
	}
}
