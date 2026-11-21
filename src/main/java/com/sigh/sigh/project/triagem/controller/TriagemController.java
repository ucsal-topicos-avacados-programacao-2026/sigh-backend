package com.sigh.sigh.project.triagem.controller;

import com.sigh.sigh.project.triagem.dto.AguardandoTriagemDTO;
import com.sigh.sigh.project.triagem.dto.ConfirmacaoRiscoRequestDTO;
import com.sigh.sigh.project.triagem.dto.PacienteNaFilaDTO;
import com.sigh.sigh.project.triagem.dto.PreClassificacaoResponseDTO;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.service.FilaAtendimentoService;
import com.sigh.sigh.project.triagem.service.SugestaoRiscoService;
import com.sigh.sigh.project.triagem.service.TriagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/triagens")
@RequiredArgsConstructor
public class TriagemController {

	private final TriagemService triagemService;
	private final FilaAtendimentoService filaAtendimentoService;
	private final SugestaoRiscoService sugestaoRiscoService;

	/** Quem aguarda triagem na instituição, com a triagem começada e não confirmada, se houver (FE-06). */
	@GetMapping("/aguardando")
	@PreAuthorize("hasRole('ENFERMEIRO')")
	public List<AguardandoTriagemDTO> aguardando(@RequestParam UUID instituicaoId, Principal usuario) {
		return filaAtendimentoService.aguardandoTriagem(instituicaoId, usuario.getName());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ENFERMEIRO')")
	public TriagemResponseDTO registrar(@RequestBody @Valid TriagemRequestDTO dados, Principal usuario) {
		return triagemService.registrar(dados, usuario.getName());
	}

	/** Sinais vitais numa triagem aberta na recepção; a IA refaz a sugestão em segundo plano. */
	@PatchMapping("/{triagemId}/sinais-vitais")
	@PreAuthorize("hasRole('ENFERMEIRO')")
	public TriagemResponseDTO registrarSinaisVitais(@PathVariable UUID triagemId,
			@RequestBody @Valid SinaisVitaisRequestDTO dados, Principal usuario) {
		return triagemService.registrarSinaisVitais(triagemId, dados.sinaisVitais(), usuario.getName());
	}

	/** Só o andamento da sugestão, nunca a cor: quem recebe o paciente não vê dado clínico. */
	@GetMapping("/pre-classificacao")
	@PreAuthorize("hasRole('RECEPCAO')")
	public PreClassificacaoResponseDTO preClassificacao(@RequestParam UUID atendimentoId,
			@RequestParam UUID instituicaoId, Principal usuario) {
		return triagemService.andamentoDaSugestao(atendimentoId, instituicaoId, usuario.getName());
	}

	@PostMapping("/{triagemId}/sugestao")
	@PreAuthorize("hasRole('ENFERMEIRO')")
	public TriagemResponseDTO sugerirRisco(@PathVariable UUID triagemId, Principal usuario) {
		return sugestaoRiscoService.sugerirRisco(triagemId, usuario.getName());
	}

	@PostMapping("/{triagemId}/confirmacao")
	@PreAuthorize("hasRole('ENFERMEIRO')")
	public TriagemResponseDTO confirmarRisco(@PathVariable UUID triagemId,
			@RequestBody @Valid ConfirmacaoRiscoRequestDTO dados, Principal usuario) {
		return triagemService.confirmarRisco(triagemId, dados.cor(), usuario.getName());
	}

	@GetMapping("/fila")
	@PreAuthorize("hasAnyRole('ENFERMEIRO', 'MEDICO')")
	public PagedModel<PacienteNaFilaDTO> consultarFila(@RequestParam UUID instituicaoId, Pageable paginacao,
			Principal usuario) {
		return new PagedModel<>(filaAtendimentoService.consultarFila(instituicaoId, usuario.getName(), paginacao));
	}
}
