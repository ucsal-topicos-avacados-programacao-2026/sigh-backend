package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.paciente.dto.RepresentacaoRequest;
import com.sigh.sigh.project.paciente.dto.RepresentacaoResponse;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.paciente.repository.RepresentacaoLegalRepository;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * O Administrativo liga a conta de acesso ao cadastro do paciente e registra o representante legal
 * (RF-002, RF-009). Ele precisa de vínculo ativo numa instituição que já atendeu o paciente: quem não
 * tem relação com o paciente não consegue dar a terceiros acesso aos dados dele.
 */
@Service
@RequiredArgsConstructor
public class AcessoAoCadastroService {

	static final String SEM_RELACAO = "A instituição não atendeu este paciente.";

	private final PacienteRepository pacienteRepository;
	private final RepresentacaoLegalRepository representacaoRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final UsuarioService usuarioService;
	private final VinculoService vinculoService;
	private final Clock clock;

	@Transactional
	public void vincularConta(UUID pacienteId, UUID instituicaoId, String loginConta, String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(instituicaoId, pacienteId, loginAdministrativo);
		if (paciente.getConta() != null) {
			throw new RegraNegocioException("O paciente já tem conta de acesso vinculada.");
		}
		Usuario conta = usuarioComPerfilPaciente(loginConta);
		if (pacienteRepository.findByContaLogin(conta.getLogin()).isPresent()) {
			throw new RegraNegocioException("Essa conta já está vinculada a outro paciente.");
		}
		paciente.setConta(conta);
		// conta_id é UNIQUE no banco: dois vínculos simultâneos da mesma conta resultam em 409.
		pacienteRepository.saveAndFlush(paciente);
	}

	@Transactional(readOnly = true)
	public List<RepresentacaoResponse> listarRepresentacoes(UUID pacienteId, UUID instituicaoId, String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(instituicaoId, pacienteId, loginAdministrativo);
		LocalDateTime agora = agora();
		return representacaoRepository.doPaciente(paciente.getId()).stream()
				.map(representacao -> RepresentacaoResponse.de(representacao, agora)).toList();
	}

	@Transactional
	public RepresentacaoResponse registrarRepresentacao(UUID pacienteId, RepresentacaoRequest requisicao,
			String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(requisicao.instituicaoId(), pacienteId, loginAdministrativo);
		Usuario representante = usuarioComPerfilPaciente(requisicao.login());
		if (paciente.getConta() != null && paciente.getConta().getId().equals(representante.getId())) {
			throw new RegraNegocioException("O paciente não pode ser representante de si mesmo.");
		}
		LocalDateTime agora = agora();
		LocalDateTime inicio = requisicao.inicioEm() == null ? agora : requisicao.inicioEm().truncatedTo(ChronoUnit.MICROS);
		LocalDateTime fim = requisicao.fimEm() == null ? null : requisicao.fimEm().truncatedTo(ChronoUnit.MICROS);
		if (fim != null && !fim.isAfter(inicio)) {
			throw new RegraNegocioException("O fim da representação precisa ser depois do início.");
		}
		if (representacaoRepository.existeVigente(paciente.getId(), representante.getLogin(), agora)) {
			throw new RegraNegocioException("Esse representante já tem representação vigente.");
		}
		RepresentacaoLegal representacao = new RepresentacaoLegal();
		representacao.setPaciente(paciente);
		representacao.setRepresentante(representante);
		representacao.setDocumento(requisicao.documento().strip());
		representacao.setInicioEm(inicio);
		representacao.setFimEm(fim);
		return RepresentacaoResponse.de(representacaoRepository.save(representacao), agora);
	}

	@Transactional
	public RepresentacaoResponse encerrarRepresentacao(UUID pacienteId, UUID representacaoId, UUID instituicaoId,
			String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(instituicaoId, pacienteId, loginAdministrativo);
		RepresentacaoLegal representacao = representacaoRepository.findById(representacaoId)
				.filter(encontrada -> encontrada.getPaciente().getId().equals(paciente.getId()))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Representação não encontrada."));
		LocalDateTime agora = agora();
		if (representacao.getFimEm() != null && !representacao.getFimEm().isAfter(agora)) {
			throw new RegraNegocioException("A representação já foi encerrada.");
		}
		representacao.setFimEm(agora);
		return RepresentacaoResponse.de(representacao, agora);
	}

	/** Vínculo administrativo ativo e paciente já atendido pela instituição; senão, 403 sem dizer se ele existe. */
	private Paciente pacienteAtendidoPela(UUID instituicaoId, UUID pacienteId, String loginAdministrativo) {
		vinculoService.exigirVinculoAtivo(loginAdministrativo, instituicaoId, Perfil.ADMINISTRATIVO);
		if (!atendimentoRepository.existsByProntuarioPacienteIdAndInstituicaoId(pacienteId, instituicaoId)) {
			throw new AccessDeniedException(SEM_RELACAO);
		}
		return pacienteRepository.findById(pacienteId).orElseThrow(() -> new AccessDeniedException(SEM_RELACAO));
	}

	private Usuario usuarioComPerfilPaciente(String login) {
		Usuario usuario = usuarioService.buscarPorLogin(login.strip());
		if (!usuario.getPerfis().contains(Perfil.PACIENTE)) {
			throw new RegraNegocioException("A conta precisa ter o perfil Paciente.");
		}
		return usuario;
	}

	/** Hora atual na mesma precisão do PostgreSQL (microssegundos). */
	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
