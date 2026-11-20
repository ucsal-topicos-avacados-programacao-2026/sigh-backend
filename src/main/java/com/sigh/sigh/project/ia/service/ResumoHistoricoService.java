package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.service.RegistroAuditoriaService;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.ia.config.IAProperties;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import com.sigh.sigh.project.ia.entity.ResumoHistoricoIA;
import com.sigh.sigh.project.ia.entity.StatusResumo;
import com.sigh.sigh.project.ia.repository.ResumoHistoricoIARepository;
import com.sigh.sigh.project.prontuario.dto.ConsultaHistoricoDTO;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.AtendimentoRepository;
import com.sigh.sigh.project.prontuario.service.DescricaoHistorico;
import com.sigh.sigh.project.prontuario.service.HistoricoAutorizadoService;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResumoHistoricoService {

	private static final String FINALIDADE_GERACAO = "Resumo do histórico por IA";
	private static final String FINALIDADE_CONSULTA = "Consulta de resumo gerado por IA";

	private final ResumoHistoricoIARepository resumoHistoricoIARepository;
	private final AtendimentoRepository atendimentoRepository;
	private final HistoricoAutorizadoService historicoAutorizadoService;
	private final RegistroAuditoriaService registroAuditoriaService;
	private final UsuarioService usuarioService;
	private final IAProperties iaProperties;
	private final Clock clock;
	private final VinculoService vinculoService;
	private final FilaDeResumosIA filaDeResumos;

	@Transactional
	public CasoResumoDTO solicitar(UUID atendimentoId, String loginMedico) {
		Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado."));
		vinculoService.exigirVinculoAtivo(loginMedico, atendimento.getInstituicao().getId(), Perfil.MEDICO);
		List<RegistroClinico> historico = historicoAutorizadoService.consultar(atendimento,
				new ConsultaHistoricoDTO(loginMedico, Perfil.MEDICO, FINALIDADE_GERACAO,
						iaProperties.limiteRegistrosHistorico()));
		if (historico.isEmpty()) {
			throw new RegraNegocioException("Não há histórico autorizado para resumir.");
		}
		ResumoHistoricoIA resumo = new ResumoHistoricoIA();
		resumo.setAtendimento(atendimento);
		resumo.setSolicitante(usuarioService.buscarPorLogin(loginMedico));
		resumo.setStatus(StatusResumo.SOLICITADO);
		resumo.setFontes(new HashSet<>(historico));
		resumoHistoricoIARepository.save(resumo);
		return new CasoResumoDTO(resumo.getId(), DescricaoHistorico.descrever(historico));
	}

	/**
	 * Grava o texto só se o resumo ainda estiver em geração. Se ele já foi dado como falho (a subida de outra
	 * instância, por exemplo), o texto é descartado, para não ressuscitar um resumo que o médico já viu falhar.
	 */
	@Transactional
	public void concluir(UUID resumoId, String texto) {
		ResumoHistoricoIA resumo = buscar(resumoId);
		if (resumo.getStatus() != StatusResumo.SOLICITADO) {
			return;
		}
		resumo.setTexto(texto);
		resumo.setGeradoEm(LocalDateTime.now(clock));
		resumo.setModeloVersao(iaProperties.modelo());
		resumo.setStatus(StatusResumo.GERADO);
	}

	/** A resposta do pedido: sempre sem texto, mesmo que a geração já tenha terminado, para o texto só sair na consulta. */
	@Transactional(readOnly = true)
	public ResumoHistoricoResponseDTO emGeracao(UUID resumoId) {
		return ResumoHistoricoResponseDTO.emGeracao(buscar(resumoId));
	}

	/** Se o solicitante ainda tem vínculo e as fontes ainda são visíveis: conferido antes de mandar à IA. */
	@Transactional(readOnly = true)
	public boolean continuaAutorizado(UUID resumoId) {
		ResumoHistoricoIA resumo = buscar(resumoId);
		try {
			vinculoService.exigirVinculoAtivo(resumo.getSolicitante().getLogin(),
					resumo.getAtendimento().getInstituicao().getId(), Perfil.MEDICO);
		} catch (AccessDeniedException semVinculo) {
			return false;
		}
		return fontesContinuamVisiveis(resumo);
	}

	@Transactional
	public void registrarFalha(UUID resumoId) {
		buscar(resumoId).setStatus(StatusResumo.FALHOU);
	}

	@Transactional
	public ResumoHistoricoResponseDTO validar(UUID resumoId, String loginMedico) {
		ResumoHistoricoIA resumo = buscarComVinculo(resumoId, loginMedico);
		if (!fontesContinuamVisiveis(resumo)) {
			throw new RegraNegocioException("O resumo usa informação restrita depois da geração. Gere um novo resumo.");
		}
		if (resumo.getStatus() != StatusResumo.GERADO) {
			throw new RegraNegocioException("Só um resumo gerado pode ser validado.");
		}
		resumo.setValidador(usuarioService.buscarPorLogin(loginMedico));
		resumo.setStatus(StatusResumo.VALIDADO);
		return ResumoHistoricoResponseDTO.de(resumo);
	}

	@Transactional
	public ResumoHistoricoResponseDTO consultar(UUID resumoId, String loginMedico) {
		// A fila é consultada ANTES de ler o resumo. A tarefa grava o resultado e só depois sai da fila; então, se ele
		// já está fora da fila aqui, a leitura abaixo já vê o que a tarefa gravou. Na ordem inversa, a leitura podia
		// pegar o resumo ainda em geração e esta consulta o marcaria como falho, apagando o texto recém-gravado.
		boolean naFila = filaDeResumos.pendente(resumoId);
		ResumoHistoricoIA resumo = buscarComVinculo(resumoId, loginMedico);
		if (resumo.getStatus() == StatusResumo.SOLICITADO && !naFila) {
			// Em geração, mas fora da fila: a tarefa terminou sem gravar o resultado. Não vai mais ser gerado.
			resumo.setStatus(StatusResumo.FALHOU);
		}
		if (!fontesContinuamVisiveis(resumo)) {
			return ResumoHistoricoResponseDTO.oculto(resumo);
		}
		// Em geração ou falho, não há texto: a consulta não mostra dado clínico e não entra na auditoria.
		// Assim, o médico pode consultar de novo até o resumo ficar pronto sem encher a auditoria.
		if (resumo.getStatus() == StatusResumo.GERADO || resumo.getStatus() == StatusResumo.VALIDADO) {
			registrarConsulta(resumo, loginMedico);
		}
		return ResumoHistoricoResponseDTO.de(resumo);
	}

	/** O resumo traz dados clínicos: só o médico com vínculo na instituição do atendimento o vê. */
	private ResumoHistoricoIA buscarComVinculo(UUID resumoId, String loginMedico) {
		ResumoHistoricoIA resumo = buscar(resumoId);
		vinculoService.exigirVinculoAtivo(loginMedico, resumo.getAtendimento().getInstituicao().getId(), Perfil.MEDICO);
		return resumo;
	}

	private boolean fontesContinuamVisiveis(ResumoHistoricoIA resumo) {
		Set<UUID> fontes = resumo.getFontes().stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		return historicoAutorizadoService.continuamVisiveis(resumo.getAtendimento(), fontes);
	}

	private ResumoHistoricoIA buscar(UUID resumoId) {
		return resumoHistoricoIARepository.findById(resumoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Resumo não encontrado."));
	}

	private void registrarConsulta(ResumoHistoricoIA resumo, String loginMedico) {
		Atendimento atendimento = resumo.getAtendimento();
		Set<UUID> registrosDeOrigem = resumo.getFontes().stream().map(RegistroClinico::getId).collect(Collectors.toSet());
		registroAuditoriaService.registrarAcesso(new AcessoProntuarioDTO(
				atendimento.getProntuario().getId(),
				loginMedico,
				Perfil.MEDICO,
				atendimento.getInstituicao().getId(),
				FINALIDADE_CONSULTA,
				false,
				null,
				registrosDeOrigem));
	}
}
