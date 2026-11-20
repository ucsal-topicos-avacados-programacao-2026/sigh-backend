package com.sigh.sigh.project.paciente.controller;

import com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO;
import com.sigh.sigh.project.auditoria.service.ConsultaAuditoriaService;
import com.sigh.sigh.project.paciente.dto.AlvoDaRestricaoRequest;
import com.sigh.sigh.project.paciente.dto.AutorizacaoAdicionalRequest;
import com.sigh.sigh.project.paciente.dto.AutorizacaoAdicionalResponse;
import com.sigh.sigh.project.paciente.dto.ConsentimentoResponse;
import com.sigh.sigh.project.paciente.dto.ConsentimentosDoPacienteResponse;
import com.sigh.sigh.project.paciente.dto.DecisaoPacienteRequest;
import com.sigh.sigh.project.paciente.dto.PacienteGerenciadoResponse;
import com.sigh.sigh.project.paciente.dto.RestricaoResponse;
import com.sigh.sigh.project.paciente.service.AutorizacaoAdicionalService;
import com.sigh.sigh.project.paciente.service.ConsentimentoService;
import com.sigh.sigh.project.paciente.service.HistoricoDoPacienteService;
import com.sigh.sigh.project.paciente.service.RestricaoService;
import com.sigh.sigh.project.paciente.service.TitularidadeService;
import com.sigh.sigh.project.prontuario.dto.RegistroDoHistoricoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Rotas do paciente ou do representante legal vigente sobre os dados do paciente: histórico (U1),
 * consentimento amplo (RF-002), restrições e autorizações adicionais (RF-009).
 */
@RestController
@RequestMapping("/api/pacientes")
@PreAuthorize("hasRole('PACIENTE')")
@RequiredArgsConstructor
public class DadosDoPacienteController {

	private final TitularidadeService titularidadeService;
	private final HistoricoDoPacienteService historicoDoPacienteService;
	private final ConsentimentoService consentimentoService;
	private final RestricaoService restricaoService;
	private final AutorizacaoAdicionalService autorizacaoAdicionalService;
	private final ConsultaAuditoriaService consultaAuditoriaService;

	/** Pacientes que o usuário gerencia: o próprio cadastro e os representados. */
	@GetMapping("/gerenciados")
	public List<PacienteGerenciadoResponse> gerenciados(Principal usuario) {
		return titularidadeService.gerenciados(usuario.getName());
	}

	@GetMapping("/{pacienteId}/historico")
	public List<RegistroDoHistoricoDTO> historico(@PathVariable UUID pacienteId, Principal usuario) {
		return historicoDoPacienteService.consultar(pacienteId, usuario.getName());
	}

	/** Quem acessou o prontuário (RF-008), também para o representante legal (BE-19). */
	@GetMapping("/{pacienteId}/acessos")
	public PagedModel<AcessoDoPacienteDTO> acessos(@PathVariable UUID pacienteId, Pageable paginacao, Principal usuario) {
		return new PagedModel<>(consultaAuditoriaService.consultarAcessosDoPaciente(pacienteId, usuario.getName(), paginacao));
	}

	@GetMapping("/{pacienteId}/consentimentos")
	public ConsentimentosDoPacienteResponse consentimentos(@PathVariable UUID pacienteId, Principal usuario) {
		return consentimentoService.consultarDoPaciente(pacienteId, usuario.getName());
	}

	@PostMapping("/{pacienteId}/consentimentos")
	@ResponseStatus(HttpStatus.CREATED)
	public ConsentimentoResponse decidir(@PathVariable UUID pacienteId,
			@RequestBody @Valid DecisaoPacienteRequest requisicao, Principal usuario) {
		return consentimentoService.registrarDecisaoDoPaciente(pacienteId, usuario.getName(), requisicao.decisao());
	}

	@GetMapping("/{pacienteId}/restricoes")
	public List<RestricaoResponse> restricoes(@PathVariable UUID pacienteId, Principal usuario) {
		return restricaoService.listar(pacienteId, usuario.getName());
	}

	@PostMapping("/{pacienteId}/restricoes")
	@ResponseStatus(HttpStatus.CREATED)
	public RestricaoResponse restringir(@PathVariable UUID pacienteId,
			@RequestBody @Valid AlvoDaRestricaoRequest requisicao, Principal usuario) {
		return restricaoService.restringir(pacienteId, requisicao, usuario.getName());
	}

	@PostMapping("/{pacienteId}/restricoes/{restricaoId}/encerramento")
	public RestricaoResponse encerrarRestricao(@PathVariable UUID pacienteId, @PathVariable UUID restricaoId,
			Principal usuario) {
		return restricaoService.encerrar(pacienteId, restricaoId, usuario.getName());
	}

	@GetMapping("/{pacienteId}/autorizacoes")
	public List<AutorizacaoAdicionalResponse> autorizacoes(@PathVariable UUID pacienteId, Principal usuario) {
		return autorizacaoAdicionalService.listarVigentes(pacienteId, usuario.getName());
	}

	@PostMapping("/{pacienteId}/autorizacoes")
	@ResponseStatus(HttpStatus.CREATED)
	public AutorizacaoAdicionalResponse autorizar(@PathVariable UUID pacienteId,
			@RequestBody @Valid AutorizacaoAdicionalRequest requisicao, Principal usuario) {
		return autorizacaoAdicionalService.conceder(pacienteId, requisicao, usuario.getName());
	}

	@PostMapping("/{pacienteId}/autorizacoes/{autorizacaoId}/revogacao")
	@ResponseStatus(HttpStatus.CREATED)
	public AutorizacaoAdicionalResponse revogarAutorizacao(@PathVariable UUID pacienteId,
			@PathVariable UUID autorizacaoId, Principal usuario) {
		return autorizacaoAdicionalService.revogar(pacienteId, autorizacaoId, usuario.getName());
	}
}
