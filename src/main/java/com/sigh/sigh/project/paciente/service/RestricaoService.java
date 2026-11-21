package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.paciente.dto.AlvoDaRestricaoRequest;
import com.sigh.sigh.project.paciente.dto.RestricaoResponse;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RestricaoAcesso;
import com.sigh.sigh.project.paciente.repository.RestricaoAcessoRepository;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestricaoService {

	private final TitularidadeService titularidadeService;
	private final ProntuarioRepository prontuarioRepository;
	private final RestricaoAcessoRepository restricaoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<RestricaoResponse> listar(UUID pacienteId, String login) {
		Prontuario prontuario = prontuarioGerenciado(pacienteId, login);
		LocalDateTime agora = agora();
		return restricaoRepository.doProntuario(prontuario.getId()).stream()
				.map(restricao -> RestricaoResponse.de(restricao, agora)).toList();
	}

	@Transactional
	public RestricaoResponse restringir(UUID pacienteId, AlvoDaRestricaoRequest requisicao, String login) {
		Prontuario prontuario = prontuarioGerenciado(pacienteId, login);
		AlvoDeRestricao alvo = AlvoDeRestricao.de(requisicao.categoria(), requisicao.registroId(), prontuario,
				registroClinicoRepository);
		LocalDateTime agora = agora();
		boolean jaRestrito = alvo.registro() != null
				? restricaoRepository.existeVigenteDoRegistro(prontuario.getId(), alvo.registro().getId(), agora)
				: restricaoRepository.existeVigenteDaCategoria(prontuario.getId(), alvo.categoria(), agora);
		if (jaRestrito) {
			throw new RegraNegocioException("Essa informação já está restrita.");
		}
		RestricaoAcesso restricao = new RestricaoAcesso();
		restricao.setProntuario(prontuario);
		restricao.setCategoriaAlvo(alvo.categoria());
		restricao.setRegistroAlvo(alvo.registro());
		restricao.setInicioEm(agora);
		restricao.setDefinidaPor(usuarioService.buscarPorLogin(login));
		return RestricaoResponse.de(restricaoRepository.save(restricao), agora);
	}

	@Transactional
	public RestricaoResponse encerrar(UUID pacienteId, UUID restricaoId, String login) {
		Prontuario prontuario = prontuarioGerenciado(pacienteId, login);
		RestricaoAcesso restricao = restricaoRepository.findById(restricaoId)
				.filter(encontrada -> encontrada.getProntuario().getId().equals(prontuario.getId()))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Restrição não encontrada."));
		LocalDateTime agora = agora();
		if (restricao.getFimEm() != null && !restricao.getFimEm().isAfter(agora)) {
			throw new RegraNegocioException("A restrição já foi encerrada.");
		}
		restricao.setFimEm(agora);
		restricao.setEncerradaPor(usuarioService.buscarPorLogin(login));
		return RestricaoResponse.de(restricao, agora);
	}

	private Prontuario prontuarioGerenciado(UUID pacienteId, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		return prontuarioRepository.findByPacienteId(paciente.getId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Prontuário não encontrado."));
	}

	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
