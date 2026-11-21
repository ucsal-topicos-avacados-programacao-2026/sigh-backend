package com.sigh.sigh.project.painel.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.painel.dto.ChegadaNaFila;
import com.sigh.sigh.project.painel.dto.ContagemComChegada;
import com.sigh.sigh.project.painel.dto.LeitosDoPainelDTO;
import com.sigh.sigh.project.painel.dto.PainelDaInstituicaoDTO;
import com.sigh.sigh.project.painel.dto.PainelDaInstituicaoDTO.EsperaDTO;
import com.sigh.sigh.project.painel.dto.PainelDaInstituicaoDTO.RiscoNaFilaDTO;
import com.sigh.sigh.project.painel.dto.PainelDaInstituicaoDTO.TransferenciasPendentesDTO;
import com.sigh.sigh.project.painel.repository.PainelRepository;
import com.sigh.sigh.project.triagem.config.TempoMaximoEsperaProperties;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.service.VinculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PainelService {

	private final PainelRepository painelRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final VinculoService vinculoService;
	private final TempoMaximoEsperaProperties tempoMaximoEspera;
	private final Clock clock;

	/**
	 * Números da instituição agora (FE-09). O auditor/gestor vê qualquer instituição da rede, como nos indicadores
	 * de auditoria (RF-011). Médico, enfermeiro e administrativo precisam de vínculo ativo com ela (RF-012): sem
	 * vínculo, 403 antes de procurar a instituição, para não revelar se ela existe.
	 */
	@Transactional(readOnly = true)
	public PainelDaInstituicaoDTO daInstituicao(UUID instituicaoId, String login, boolean gestorDaRede) {
		if (!gestorDaRede) {
			vinculoService.exigirVinculoAtivo(login, instituicaoId, Perfil.MEDICO, Perfil.ENFERMEIRO,
					Perfil.ADMINISTRATIVO);
		}
		Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Instituição não encontrada."));
		LocalDateTime agora = LocalDateTime.now(clock);
		ContagemComChegada aguardando = painelRepository.aguardandoTriagem(instituicaoId);
		return new PainelDaInstituicaoDTO(
				instituicao.getId(),
				instituicao.getNome(),
				instituicao.getTipo(),
				agora,
				leitos(instituicaoId),
				new EsperaDTO(aguardando.quantidade(), minutosDesde(aguardando.chegadaMaisAntiga(), agora)),
				fila(painelRepository.naFila(instituicaoId), agora),
				painelRepository.emAtendimento(instituicaoId),
				painelRepository.internados(instituicaoId),
				painelRepository.chegadasDesde(instituicaoId, LocalDate.now(clock).atStartOfDay()),
				new TransferenciasPendentesDTO(painelRepository.transferenciasRecebidasPendentes(instituicaoId),
						painelRepository.transferenciasEnviadasPendentes(instituicaoId)));
	}

	/** Na ordem das categorias e das situações, para a resposta não depender da ordem do banco. */
	private List<LeitosDoPainelDTO> leitos(UUID instituicaoId) {
		return painelRepository.leitos(instituicaoId).stream()
				.sorted(Comparator.comparing(LeitosDoPainelDTO::categoria).thenComparing(LeitosDoPainelDTO::status))
				.toList();
	}

	/** As cinco cores, mesmo sem ninguém, com o mesmo critério de tempo máximo da fila do enfermeiro. */
	private List<RiscoNaFilaDTO> fila(List<ChegadaNaFila> naFila, LocalDateTime agora) {
		return Arrays.stream(CorRisco.values()).map(cor -> {
			Duration tempoMaximo = tempoMaximoEspera.da(cor);
			List<LocalDateTime> chegadas = naFila.stream().filter(item -> item.cor() == cor)
					.map(ChegadaNaFila::chegadaEm).toList();
			LocalDateTime maisAntiga = chegadas.stream().min(Comparator.naturalOrder()).orElse(null);
			long acima = chegadas.stream()
					.filter(chegada -> Duration.between(chegada, agora).compareTo(tempoMaximo) > 0).count();
			return new RiscoNaFilaDTO(cor, chegadas.size(), minutosDesde(maisAntiga, agora), tempoMaximo.toMinutes(),
					acima);
		}).toList();
	}

	private static Long minutosDesde(LocalDateTime inicio, LocalDateTime agora) {
		return inicio == null ? null : Math.max(0, Duration.between(inicio, agora).toMinutes());
	}
}
