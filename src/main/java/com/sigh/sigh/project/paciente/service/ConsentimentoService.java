package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.paciente.dto.ConsentimentoResponse;
import com.sigh.sigh.project.paciente.dto.ConsentimentosDoPacienteResponse;
import com.sigh.sigh.project.paciente.dto.DecisaoPacienteRequest.DecisaoPaciente;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConsentimentoService {

	private final ConsentimentoRepository consentimentoRepository;
	private final PacienteRepository pacienteRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final TitularidadeService titularidadeService;
	private final UsuarioService usuarioService;
	private final Clock clock;

	public ConsentimentoService(ConsentimentoRepository consentimentoRepository,
			PacienteRepository pacienteRepository, AtendimentoRepository atendimentoRepository,
			InstituicaoRepository instituicaoRepository, TitularidadeService titularidadeService,
			UsuarioService usuarioService, Clock clock) {
		this.consentimentoRepository = consentimentoRepository;
		this.pacienteRepository = pacienteRepository;
		this.atendimentoRepository = atendimentoRepository;
		this.instituicaoRepository = instituicaoRepository;
		this.titularidadeService = titularidadeService;
		this.usuarioService = usuarioService;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void registrarManifestacaoInicial(Paciente paciente, DecisaoConsentimento decisao, LocalDateTime agora) {
		novaVersao(paciente, decisao, null, null, agora);
	}

	@Transactional(readOnly = true)
	public ConsentimentosDoPacienteResponse consultarDoPaciente(String login) {
		return consultar(pacienteDoUsuario(login));
	}

	@Transactional(readOnly = true)
	public ConsentimentosDoPacienteResponse consultarDoPaciente(UUID pacienteId, String login) {
		return consultar(titularidadeService.pacienteGerenciado(login, pacienteId));
	}

	private ConsentimentosDoPacienteResponse consultar(Paciente paciente) {
		ConsentimentoResponse vigente = consentimentoRepository.decisaoAmplaVigente(paciente.getId())
				.map(ConsentimentoResponse::de)
				.orElse(null);
		return new ConsentimentosDoPacienteResponse(vigente,
				consentimentoRepository.historico(paciente.getId()).stream().map(ConsentimentoResponse::de).toList());
	}

	@Transactional
	public ConsentimentoResponse registrarDecisaoDoPaciente(String login, DecisaoPaciente decisao) {
		return registrarDecisao(pacienteDoUsuario(login), login, decisao);
	}

	@Transactional
	public ConsentimentoResponse registrarDecisaoDoPaciente(UUID pacienteId, String login, DecisaoPaciente decisao) {
		return registrarDecisao(titularidadeService.pacienteGerenciado(login, pacienteId), login, decisao);
	}

	private ConsentimentoResponse registrarDecisao(Paciente paciente, String login, DecisaoPaciente decisao) {
		Optional<Consentimento> vigente = consentimentoRepository.decisaoAmplaVigente(paciente.getId());
		boolean autorizadoAgora = vigente.map(c -> c.getDecisao() == DecisaoConsentimento.AUTORIZADO).orElse(false);

		DecisaoConsentimento nova = switch (decisao) {
			case AUTORIZADO -> {
				if (autorizadoAgora) {
					throw new RegraNegocioException("O compartilhamento já está autorizado.");
				}
				yield DecisaoConsentimento.AUTORIZADO;
			}
			case REVOGADO -> {
				if (!autorizadoAgora) {
					throw new RegraNegocioException("Não há autorização vigente para revogar.");
				}
				yield DecisaoConsentimento.REVOGADO;
			}
		};
		Consentimento registrado = novaVersao(paciente, nova, vigente.orElse(null),
				usuarioService.buscarPorLogin(login), agora());
		return ConsentimentoResponse.de(registrado);
	}

	@Transactional(readOnly = true)
	public boolean podeCompartilharCom(UUID pacienteId, UUID instituicaoId) {
		boolean credenciada = instituicaoRepository.findById(instituicaoId)
				.map(instituicao -> instituicao.isCredenciada())
				.orElse(false);
		if (!credenciada) {
			return false;
		}
		return consentimentoRepository.decisaoAmplaVigente(pacienteId)
				.map(decisao -> switch (decisao.getDecisao()) {
					case AUTORIZADO -> true;
					case NEGADO -> false;
					case REVOGADO -> atendimentoRepository.existsByProntuarioPacienteIdAndInstituicaoIdAndChegadaEmLessThanEqual(
							pacienteId, instituicaoId, decisao.getVigenteDesde());
				})
				.orElse(false);
	}

	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private Paciente pacienteDoUsuario(String login) {
		return pacienteRepository.findByContaLogin(login)
				.orElseThrow(() -> new AccessDeniedException("Usuário sem cadastro de paciente vinculado."));
	}

	private Consentimento novaVersao(Paciente paciente, DecisaoConsentimento decisao, Consentimento anterior,
			Usuario outorgante, LocalDateTime agora) {
		Consentimento consentimento = new Consentimento();
		consentimento.setTipo(TipoConsentimento.AMPLO);
		consentimento.setDecisao(decisao);
		consentimento.setAnterior(anterior);
		consentimento.setOutorgante(outorgante);
		return gravarVersao(paciente, consentimento, agora);
	}

	Consentimento gravarVersao(Paciente paciente, Consentimento consentimento, LocalDateTime agora) {
		consentimento.setPaciente(paciente);
		consentimento.setVersao(consentimentoRepository.ultimaVersao(paciente.getId()) + 1);
		consentimento.setVigenteDesde(agora);
		return consentimentoRepository.saveAndFlush(consentimento);
	}
}
