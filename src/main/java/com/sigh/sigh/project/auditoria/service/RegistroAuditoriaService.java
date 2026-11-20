package com.sigh.sigh.project.auditoria.service;

import com.sigh.sigh.project.auditoria.dto.AcessoProntuarioDTO;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.auditoria.repository.RegistroAuditoriaRepository;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistroAuditoriaService {

	private final RegistroAuditoriaRepository registroAuditoriaRepository;
	private final ProntuarioRepository prontuarioRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final UsuarioService usuarioService;
	private final Clock clock;

	@Transactional
	public void registrarAcesso(AcessoProntuarioDTO acesso) {
		if (acesso.emergencia() && isVazio(acesso.justificativa())) {
			throw new RegraNegocioException("Acesso de emergência exige justificativa.");
		}
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setProntuario(prontuarioRepository.getReferenceById(acesso.prontuarioId()));
		registro.setAtor(usuarioService.buscarPorLogin(acesso.loginAtor()));
		registro.setPerfil(acesso.perfil());
		registro.setInstituicao(instituicaoRepository.getReferenceById(acesso.instituicaoId()));
		registro.setOcorridoEm(LocalDateTime.now(clock));
		registro.setFinalidadeDeclarada(acesso.finalidade());
		registro.setEmergencia(acesso.emergencia());
		registro.setJustificativa(acesso.justificativa());
		registro.setRegistrosAcessados(referenciasDosRegistros(acesso.registrosAcessadosIds()));
		registroAuditoriaRepository.save(registro);
	}

	private Set<RegistroClinico> referenciasDosRegistros(Set<UUID> ids) {
		return ids.stream()
				.map(registroClinicoRepository::getReferenceById)
				.collect(Collectors.toSet());
	}

	private boolean isVazio(String texto) {
		return texto == null || texto.isBlank();
	}
}
