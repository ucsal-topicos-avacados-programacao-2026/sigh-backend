package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.paciente.dto.RepresentacaoRequest;
import com.sigh.sigh.project.paciente.dto.RepresentacaoResponse;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.paciente.repository.RepresentacaoLegalRepository;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Recepção liga a conta de acesso ao cadastro do paciente e registra o representante legal
 * (RF-002, RF-009). Ele precisa de vínculo ativo numa instituição que já atendeu o paciente: quem não
 * tem relação com o paciente não consegue dar a terceiros acesso aos dados dele.
 */
@Service
@RequiredArgsConstructor
public class AcessoAoCadastroService {

	static final String SEM_RELACAO = "A instituição não atendeu este paciente.";
	static final String CONTA_INVALIDA = "A conta informada não pode ser usada para este paciente.";
	static final String FINALIDADE_CONTA = "Vínculo da conta de acesso do paciente";
	static final String FINALIDADE_REPRESENTACAO = "Registro de representante legal";
	static final String FINALIDADE_ENCERRAMENTO = "Encerramento de representação legal";

	private final PacienteRepository pacienteRepository;
	private final RepresentacaoLegalRepository representacaoRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final UsuarioRepository usuarioRepository;
	private final ProntuarioRepository prontuarioRepository;
	private final RegistroAuditoriaService registroAuditoriaService;
	private final VinculoService vinculoService;
	private final Clock clock;

	@Transactional
	public void vincularConta(UUID pacienteId, UUID instituicaoId, String loginConta, String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(instituicaoId, pacienteId, loginAdministrativo);
		if (paciente.getConta() != null) {
			throw new RegraNegocioException("O paciente já tem conta de acesso vinculada.");
		}
		Usuario conta = contaDePaciente(loginConta, loginAdministrativo);
		if (pacienteRepository.findByContaLogin(conta.getLogin()).isPresent()) {
			throw new RegraNegocioException(CONTA_INVALIDA);
		}
		paciente.setConta(conta);
		// conta_id é UNIQUE no banco: dois vínculos simultâneos da mesma conta resultam em 409.
		pacienteRepository.saveAndFlush(paciente);
		auditar(paciente, instituicaoId, loginAdministrativo, FINALIDADE_CONTA);
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
		Usuario representante = contaDePaciente(requisicao.login(), loginAdministrativo);
		if (paciente.getConta() != null && paciente.getConta().getId().equals(representante.getId())) {
			throw new RegraNegocioException(CONTA_INVALIDA);
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
		RepresentacaoLegal registrada = representacaoRepository.save(representacao);
		auditar(paciente, requisicao.instituicaoId(), loginAdministrativo, FINALIDADE_REPRESENTACAO);
		return RepresentacaoResponse.de(registrada, agora);
	}

	@Transactional
	public RepresentacaoResponse encerrarRepresentacao(UUID pacienteId, UUID representacaoId, UUID instituicaoId,
			String loginAdministrativo) {
		Paciente paciente = pacienteAtendidoPela(instituicaoId, pacienteId, loginAdministrativo);
		RepresentacaoLegal representacao = representacaoRepository.findById(representacaoId)
				.filter(encontrada -> encontrada.getPaciente().getId().equals(paciente.getId()))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Representação não encontrada."));
		LocalDateTime agora = agora();
		boolean jaEncerrada = representacao.getFimEm() != null
				&& (!representacao.getFimEm().isAfter(agora) || representacao.getFimEm().equals(representacao.getInicioEm()));
		if (jaEncerrada) {
			throw new RegraNegocioException("A representação já foi encerrada.");
		}
		representacao.setFimEm(agora.isBefore(representacao.getInicioEm()) ? representacao.getInicioEm() : agora);
		auditar(paciente, instituicaoId, loginAdministrativo, FINALIDADE_ENCERRAMENTO);
		return RepresentacaoResponse.de(representacao, agora);
	}

	/** Vínculo de recepção ativo e paciente já atendido pela instituição; senão, 403 sem dizer se ele existe. */
	private Paciente pacienteAtendidoPela(UUID instituicaoId, UUID pacienteId, String loginAdministrativo) {
		vinculoService.exigirVinculoAtivo(loginAdministrativo, instituicaoId, Perfil.RECEPCAO);
		if (!atendimentoRepository.existsByProntuarioPacienteIdAndInstituicaoId(pacienteId, instituicaoId)) {
			throw new AccessDeniedException(SEM_RELACAO);
		}
		return pacienteRepository.findById(pacienteId).orElseThrow(() -> new AccessDeniedException(SEM_RELACAO));
	}

	/**
	 * Conta com perfil Paciente que não seja a do próprio recepcionista. Login inexistente, perfil
	 * errado e conta já usada dão a mesma resposta, para não revelar quais logins existem.
	 */
	private Usuario contaDePaciente(String login, String loginAdministrativo) {
		String informado = login.strip();
		if (informado.equals(loginAdministrativo)) {
			throw new RegraNegocioException(CONTA_INVALIDA);
		}
		Usuario usuario = usuarioRepository.findByLogin(informado)
				.orElseThrow(() -> new RegraNegocioException(CONTA_INVALIDA));
		if (!usuario.getPerfis().contains(Perfil.PACIENTE)) {
			throw new RegraNegocioException(CONTA_INVALIDA);
		}
		return usuario;
	}

	/** Dar acesso ao prontuário a alguém fica na auditoria imutável (RN-006), com quem fez e onde. */
	private void auditar(Paciente paciente, UUID instituicaoId, String loginAdministrativo, String finalidade) {
		UUID prontuarioId = prontuarioRepository.findByPacienteId(paciente.getId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Prontuário não encontrado.")).getId();
		registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(prontuarioId, loginAdministrativo,
				Perfil.RECEPCAO, instituicaoId, finalidade, false, null, Set.of()));
	}

	/** Hora atual na mesma precisão do PostgreSQL (microssegundos). */
	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
