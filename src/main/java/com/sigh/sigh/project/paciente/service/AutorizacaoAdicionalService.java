package com.sigh.sigh.project.paciente.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.paciente.dto.AutorizacaoAdicionalRequest;
import com.sigh.sigh.project.paciente.dto.AutorizacaoAdicionalResponse;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.paciente.repository.ConsentimentoRepository;
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

/**
 * Autorização adicional explícita (RF-009): o paciente ou representante libera para uma instituição
 * uma categoria ou um registro restrito. Cada concessão e cada revogação é uma nova versão do
 * consentimento (tipo ADICIONAL), ligada à anterior; nada é alterado ou apagado.
 */
@Service
@RequiredArgsConstructor
public class AutorizacaoAdicionalService {

	private final TitularidadeService titularidadeService;
	private final ConsentimentoService consentimentoService;
	private final ConsentimentoRepository consentimentoRepository;
	private final ProntuarioRepository prontuarioRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<AutorizacaoAdicionalResponse> listarVigentes(UUID pacienteId, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		return consentimentoRepository.autorizacoesAdicionaisVigentes(paciente.getId()).stream()
				.map(AutorizacaoAdicionalResponse::de).toList();
	}

	@Transactional
	public AutorizacaoAdicionalResponse conceder(UUID pacienteId, AutorizacaoAdicionalRequest requisicao, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		Prontuario prontuario = prontuarioRepository.findByPacienteId(paciente.getId())
				.orElseThrow(() -> new RecursoNaoEncontradoException("Prontuário não encontrado."));
		AlvoDeRestricao alvo = AlvoDeRestricao.de(requisicao.categoria(), requisicao.registroId(), prontuario,
				registroClinicoRepository);
		Instituicao instituicao = instituicaoRepository.findById(requisicao.instituicaoId())
				.filter(Instituicao::isCredenciada)
				.orElseThrow(() -> new RegraNegocioException("A instituição não está credenciada na rede."));
		boolean jaAutorizado = consentimentoRepository.autorizacoesAdicionaisVigentes(paciente.getId()).stream()
				.anyMatch(vigente -> mesmoAlvo(vigente, instituicao, alvo));
		if (jaAutorizado) {
			throw new RegraNegocioException("Essa autorização já está vigente.");
		}
		Consentimento autorizacao = adicional(DecisaoConsentimento.AUTORIZADO, instituicao, alvo.categoria(), null,
				login);
		autorizacao.setRegistroAlvo(alvo.registro());
		return AutorizacaoAdicionalResponse.de(consentimentoService.gravarVersao(paciente, autorizacao, agora()));
	}

	@Transactional
	public AutorizacaoAdicionalResponse revogar(UUID pacienteId, UUID autorizacaoId, String login) {
		Paciente paciente = titularidadeService.pacienteGerenciado(login, pacienteId);
		Consentimento vigente = consentimentoRepository.autorizacoesAdicionaisVigentes(paciente.getId()).stream()
				.filter(autorizacao -> autorizacao.getId().equals(autorizacaoId))
				.findFirst()
				.orElseThrow(() -> new RecursoNaoEncontradoException("Autorização vigente não encontrada."));
		Consentimento revogacao = adicional(DecisaoConsentimento.REVOGADO, vigente.getDestinataria(),
				vigente.getCategoriaAlvo(), vigente, login);
		revogacao.setRegistroAlvo(vigente.getRegistroAlvo());
		return AutorizacaoAdicionalResponse.de(consentimentoService.gravarVersao(paciente, revogacao, agora()));
	}

	private Consentimento adicional(DecisaoConsentimento decisao, Instituicao destinataria, String categoria,
			Consentimento anterior, String login) {
		Consentimento consentimento = new Consentimento();
		consentimento.setTipo(TipoConsentimento.ADICIONAL);
		consentimento.setDecisao(decisao);
		consentimento.setDestinataria(destinataria);
		consentimento.setCategoriaAlvo(categoria);
		consentimento.setAnterior(anterior);
		consentimento.setOutorgante(usuarioService.buscarPorLogin(login));
		return consentimento;
	}

	private static boolean mesmoAlvo(Consentimento vigente, Instituicao instituicao, AlvoDeRestricao alvo) {
		if (!vigente.getDestinataria().getId().equals(instituicao.getId())) {
			return false;
		}
		if (alvo.registro() != null) {
			return vigente.getRegistroAlvo() != null && vigente.getRegistroAlvo().getId().equals(alvo.registro().getId());
		}
		return vigente.getCategoriaAlvo() != null && vigente.getCategoriaAlvo().equalsIgnoreCase(alvo.categoria());
	}

	/** Hora atual na mesma precisão do PostgreSQL (microssegundos). */
	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
