package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.DadosInvalidosException;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.paciente.cpf.Cpf;
import com.sigh.sigh.project.paciente.dto.AdmissaoRequest;
import com.sigh.sigh.project.paciente.dto.AdmissaoResponse;
import com.sigh.sigh.project.paciente.dto.BuscaPacienteRequest;
import com.sigh.sigh.project.paciente.dto.PacienteResumoResponse;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.triagem.service.TriagemService;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdmissaoPacienteService {

	private final PacienteRepository pacienteRepository;
	private final ProntuarioRepository prontuarioRepository;
	private final AtendimentoRepository atendimentoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final VinculoInstitucionalRepository vinculoRepository;
	private final ConsentimentoService consentimentoService;
	private final TriagemService triagemService;
	private final Clock clock;

	public AdmissaoPacienteService(PacienteRepository pacienteRepository, ProntuarioRepository prontuarioRepository,
			AtendimentoRepository atendimentoRepository, InstituicaoRepository instituicaoRepository,
			VinculoInstitucionalRepository vinculoRepository, ConsentimentoService consentimentoService, TriagemService triagemService, Clock clock) {
		this.pacienteRepository = pacienteRepository;
		this.prontuarioRepository = prontuarioRepository;
		this.atendimentoRepository = atendimentoRepository;
		this.instituicaoRepository = instituicaoRepository;
		this.vinculoRepository = vinculoRepository;
		this.consentimentoService = consentimentoService;
		this.triagemService = triagemService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PacienteResumoResponse buscarPorCpf(BuscaPacienteRequest requisicao, String login) {
		instituicaoAutorizada(login, requisicao.instituicaoId(), agora());
		return pacienteRepository.findByCpf(Cpf.normalizar(requisicao.cpf()))
				.map(p -> new PacienteResumoResponse(p.getId(), p.getNome(), p.getDataNascimento()))
				.orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado na rede."));
	}

	@Transactional
	public AdmissaoResponse admitir(AdmissaoRequest requisicao, String login) {
		LocalDateTime agora = agora();
		Instituicao instituicao = instituicaoAutorizada(login, requisicao.instituicaoId(), agora);

		Optional<Paciente> existente = pacienteRepository.findByCpf(Cpf.normalizar(requisicao.cpf()));
		Paciente paciente;
		Prontuario prontuario;
		if (existente.isPresent()) {
			paciente = existente.get();
			prontuario = prontuarioRepository.findByPacienteId(paciente.getId())
					.orElseThrow(() -> new IllegalStateException("Paciente cadastrado sem prontuário"));
		} else {
			paciente = cadastrar(requisicao);
			prontuario = criarProntuario(paciente, agora);
			if (requisicao.consentimentoInicial() != null) {
				consentimentoService.registrarManifestacaoInicial(paciente,
						requisicao.consentimentoInicial().decisao(), agora);
			}
		}

		Atendimento atendimento = new Atendimento();
		atendimento.setProntuario(prontuario);
		atendimento.setInstituicao(instituicao);
		atendimento.setChegadaEm(agora);
		atendimento.setMotivo(requisicao.motivo().strip());
		atendimento.setStatus(StatusAtendimento.ABERTO);
		atendimentoRepository.save(atendimento);
		triagemService.abrirNaAdmissao(atendimento, login);

		return new AdmissaoResponse(paciente.getId(), prontuario.getId(), atendimento.getId(), existente.isEmpty());
	}

	private Instituicao instituicaoAutorizada(String login, UUID instituicaoId, LocalDateTime agora) {
		if (!vinculoRepository.existeVinculoAtivo(login, instituicaoId, Perfil.RECEPCAO, agora)) {
			throw new AccessDeniedException("Sem vínculo de recepção ativo com a instituição.");
		}
		Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
				.orElseThrow(() -> new AccessDeniedException("Sem vínculo de recepção ativo com a instituição."));
		if (!instituicao.isCredenciada()) {
			throw new RegraNegocioException("A instituição não está credenciada na rede.");
		}
		return instituicao;
	}

	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private Paciente cadastrar(AdmissaoRequest requisicao) {
		Map<String, String> faltando = new LinkedHashMap<>();
		if (requisicao.nome() == null || requisicao.nome().isBlank()) {
			faltando.put("nome", "Nome é obrigatório no primeiro cadastro");
		}
		if (requisicao.dataNascimento() == null) {
			faltando.put("dataNascimento", "Data de nascimento é obrigatória no primeiro cadastro");
		}
		if (!faltando.isEmpty()) {
			throw new DadosInvalidosException(faltando);
		}
		Paciente paciente = new Paciente();
		paciente.setCpf(Cpf.normalizar(requisicao.cpf()));
		paciente.setNome(requisicao.nome().strip());
		paciente.setDataNascimento(requisicao.dataNascimento());
		return pacienteRepository.saveAndFlush(paciente);
	}

	private Prontuario criarProntuario(Paciente paciente, LocalDateTime agora) {
		Prontuario prontuario = new Prontuario();
		prontuario.setPaciente(paciente);
		prontuario.setCriadoEm(agora);
		return prontuarioRepository.save(prontuario);
	}
}
