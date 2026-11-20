package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.AnaliseAuditoriaRequestDTO;
import com.sigh.sigh.project.auditoria.dto.AnaliseAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.entity.AnaliseAuditoria;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.auditoria.entity.TipoAnalise;
import com.sigh.sigh.project.auditoria.repository.AnaliseAuditoriaRepository;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnaliseAuditoriaService {

	private final AnaliseAuditoriaRepository analiseAuditoriaRepository;
	private final RegistroAuditoriaRepository registroAuditoriaRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;

	@Transactional
	public AnaliseAuditoriaResponseDTO registrar(UUID registroAuditoriaId, AnaliseAuditoriaRequestDTO dados,
			String loginAuditor) {
		RegistroAuditoria registro = registroAuditoriaRepository.findById(registroAuditoriaId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Registro de auditoria não encontrado."));
		AnaliseAuditoria analise = new AnaliseAuditoria();
		analise.setRegistroAuditoria(registro);
		analise.setTipo(dados.tipo());
		analise.setConclusao(dados.conclusao());
		analise.setDescricao(dados.descricao());
		analise.setRegistradaEm(LocalDateTime.now(clock));
		analise.setAuditor(usuarioService.buscarPorLogin(loginAuditor));
		return AnaliseAuditoriaResponseDTO.de(analiseAuditoriaRepository.save(analise));
	}

	@Transactional(readOnly = true)
	public Page<AnaliseAuditoriaResponseDTO> listar(TipoAnalise tipo, Pageable paginacao) {
		return analiseAuditoriaRepository.findByTipoOrderByRegistradaEmDesc(tipo, paginacao)
				.map(AnaliseAuditoriaResponseDTO::de);
	}
}
