package com.sigh.sigh.project.usuario.service;

import com.sigh.sigh.project.comum.erro.RecursoNaoEncontradoException;
import com.sigh.sigh.project.comum.erro.RegraNegocioException;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.usuario.dto.AcaoAdministrativaDTO;
import com.sigh.sigh.project.usuario.dto.CriarUsuarioRequest;
import com.sigh.sigh.project.usuario.dto.CriarUsuarioResponse;
import com.sigh.sigh.project.usuario.dto.SenhaTemporariaResponse;
import com.sigh.sigh.project.usuario.dto.UsuarioDetalheDTO;
import com.sigh.sigh.project.usuario.dto.VinculoDoUsuarioDTO;
import com.sigh.sigh.project.usuario.dto.VinculoRequest;
import com.sigh.sigh.project.usuario.entity.AcaoAdministrativa;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.TipoDeAcaoAdministrativa;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.AcaoAdministrativaRepository;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GestaoDeUsuariosService {

	private static final Set<Perfil> PERFIS_DE_INSTITUICAO =
			EnumSet.of(Perfil.MEDICO, Perfil.ENFERMEIRO, Perfil.ADMINISTRATIVO, Perfil.RECEPCAO);
	private static final Set<Perfil> PERFIS_COM_REGISTRO = EnumSet.of(Perfil.MEDICO, Perfil.ENFERMEIRO);

	private final UsuarioRepository usuarioRepository;
	private final VinculoInstitucionalRepository vinculoRepository;
	private final InstituicaoRepository instituicaoRepository;
	private final AcaoAdministrativaRepository acaoRepository;
	private final PasswordEncoder codificadorDeSenha;
	private final GeradorDeSenha geradorDeSenha;
	private final Clock clock;

	@Transactional
	public CriarUsuarioResponse criar(CriarUsuarioRequest dados, String loginDoAuditor) {
		if (usuarioRepository.existsByLogin(dados.login())) {
			throw new RegraNegocioException("Já existe um usuário com esse login.");
		}
		Set<Perfil> perfis = EnumSet.copyOf(dados.perfis());
		validarPerfis(perfis);
		List<VinculoRequest> vinculos = dados.vinculos() == null ? List.of() : dados.vinculos();
		validarVinculosIniciais(perfis, vinculos);

		String senha = geradorDeSenha.gerar();
		Usuario usuario = new Usuario();
		usuario.setLogin(dados.login());
		usuario.setNome(dados.nome().strip());
		usuario.setSenhaHash(codificadorDeSenha.encode(senha));
		usuario.setPerfis(perfis);
		usuario.setSenhaTemporaria(true);
		usuarioRepository.save(usuario);
		LocalDateTime agora = agora();
		for (VinculoRequest pedido : vinculos) {
			vinculoRepository.save(novoVinculo(usuario, pedido, agora));
		}
		registrar(loginDoAuditor, usuario, TipoDeAcaoAdministrativa.USUARIO_CRIADO, "Perfis: " + nomes(perfis));
		return new CriarUsuarioResponse(detalhe(usuario), senha);
	}

	@Transactional(readOnly = true)
	public Page<UsuarioDetalheDTO> listar(String busca, Perfil perfil, Boolean ativo, Pageable paginacao) {
		String termo = "%" + (busca == null ? "" : busca.strip().toLowerCase()) + "%";
		Page<Usuario> pagina = usuarioRepository.buscar(ativo == null, ativo != null && ativo, perfil == null,
				perfil == null ? Perfil.PACIENTE : perfil, termo, paginacao);
		Map<UUID, List<VinculoDoUsuarioDTO>> vinculos = vinculosPorUsuario(pagina.getContent());
		return pagina.map(usuario -> UsuarioDetalheDTO.de(usuario, vinculos.getOrDefault(usuario.getId(), List.of())));
	}

	@Transactional(readOnly = true)
	public Page<AcaoAdministrativaDTO> acoes(Pageable paginacao) {
		return acaoRepository.recentes(paginacao).map(AcaoAdministrativaDTO::de);
	}

	@Transactional
	public UsuarioDetalheDTO alterarAtivo(UUID id, boolean ativo, String loginDoAuditor) {
		Usuario alvo = buscar(id);
		if (alvo.isAtivo() == ativo) {
			return detalhe(alvo);
		}
		if (!ativo) {
			if (alvo.getLogin().equals(loginDoAuditor)) {
				throw new RegraNegocioException("Você não pode desativar o seu próprio usuário.");
			}
			if (alvo.getPerfis().contains(Perfil.AUDITOR_GESTOR)
					&& usuarioRepository.contarAtivosComPerfil(Perfil.AUDITOR_GESTOR) <= 1) {
				throw new RegraNegocioException("A rede precisa de ao menos um auditor/gestor ativo.");
			}
		}
		alvo.setAtivo(ativo);
		registrar(loginDoAuditor, alvo, ativo ? TipoDeAcaoAdministrativa.USUARIO_REATIVADO
				: TipoDeAcaoAdministrativa.USUARIO_DESATIVADO, null);
		return detalhe(alvo);
	}

	@Transactional
	public SenhaTemporariaResponse redefinirSenha(UUID id, String loginDoAuditor) {
		Usuario alvo = buscar(id);
		if (alvo.getLogin().equals(loginDoAuditor)) {
			throw new RegraNegocioException("Para trocar a sua própria senha, use Alterar senha.");
		}
		String senha = geradorDeSenha.gerar();
		alvo.setSenhaHash(codificadorDeSenha.encode(senha));
		alvo.setSenhaTemporaria(true);
		registrar(loginDoAuditor, alvo, TipoDeAcaoAdministrativa.SENHA_REDEFINIDA, null);
		return new SenhaTemporariaResponse(alvo.getLogin(), senha);
	}

	@Transactional
	public UsuarioDetalheDTO zerarSegundoFator(UUID id, String loginDoAuditor) {
		Usuario alvo = buscar(id);
		alvo.setMfaHabilitado(false);
		alvo.setMfaSegredo(null);
		registrar(loginDoAuditor, alvo, TipoDeAcaoAdministrativa.SEGUNDO_FATOR_ZERADO, null);
		return detalhe(alvo);
	}

	@Transactional
	public UsuarioDetalheDTO adicionarVinculo(UUID id, VinculoRequest pedido, String loginDoAuditor) {
		Usuario alvo = buscar(id);
		LocalDateTime agora = agora();
		Instituicao instituicao = validarVinculo(alvo.getPerfis(), pedido);
		if (vinculoRepository.existeAtivoDoUsuarioNaInstituicao(alvo.getId(), instituicao.getId(), agora)) {
			throw new RegraNegocioException("O usuário já tem vínculo ativo nessa instituição. Encerre-o antes de criar outro.");
		}
		vinculoRepository.save(novoVinculo(alvo, pedido, agora));
		registrar(loginDoAuditor, alvo, TipoDeAcaoAdministrativa.VINCULO_ADICIONADO,
				instituicao.getNome() + ": " + nomes(pedido.perfis()));
		return detalhe(alvo);
	}

	@Transactional
	public UsuarioDetalheDTO encerrarVinculo(UUID id, UUID vinculoId, String loginDoAuditor) {
		Usuario alvo = buscar(id);
		VinculoInstitucional vinculo = vinculoRepository.buscarDosUsuarios(List.of(alvo.getId())).stream()
				.filter(candidato -> candidato.getId().equals(vinculoId))
				.findFirst()
				.orElseThrow(() -> new RecursoNaoEncontradoException("Vínculo não encontrado."));
		LocalDateTime agora = agora();
		if (!vinculo.estaAtivo(agora)) {
			throw new RegraNegocioException("O vínculo já está encerrado.");
		}
		vinculo.setFimEm(agora);
		registrar(loginDoAuditor, alvo, TipoDeAcaoAdministrativa.VINCULO_ENCERRADO, vinculo.getInstituicao().getNome());
		return detalhe(alvo);
	}

	private void validarPerfis(Set<Perfil> perfis) {
		if (perfis.contains(Perfil.PACIENTE)) {
			throw new RegraNegocioException("Paciente não tem conta de acesso: ele é cadastrado pela recepção, no atendimento.");
		}
		if (perfis.contains(Perfil.AUDITOR_GESTOR) && perfis.size() > 1) {
			throw new RegraNegocioException("Auditor/Gestor não pode ser combinado com outros perfis.");
		}
	}

	private void validarVinculosIniciais(Set<Perfil> perfis, List<VinculoRequest> vinculos) {
		Set<Perfil> exigidos = EnumSet.noneOf(Perfil.class);
		perfis.stream().filter(PERFIS_DE_INSTITUICAO::contains).forEach(exigidos::add);
		if (exigidos.isEmpty() && !vinculos.isEmpty()) {
			throw new RegraNegocioException("Este perfil não tem vínculo com instituição.");
		}
		Set<Perfil> cobertos = EnumSet.noneOf(Perfil.class);
		Set<UUID> instituicoes = new HashSet<>();
		for (VinculoRequest pedido : vinculos) {
			if (!instituicoes.add(pedido.instituicaoId())) {
				throw new RegraNegocioException("Há dois vínculos para a mesma instituição.");
			}
			validarVinculo(perfis, pedido);
			cobertos.addAll(pedido.perfis());
		}
		if (!cobertos.containsAll(exigidos)) {
			throw new RegraNegocioException("Informe ao menos um vínculo para cada perfil de instituição: " + nomes(exigidos) + ".");
		}
	}

	private Instituicao validarVinculo(Set<Perfil> perfisDoUsuario, VinculoRequest pedido) {
		for (Perfil perfil : pedido.perfis()) {
			if (!PERFIS_DE_INSTITUICAO.contains(perfil) || !perfisDoUsuario.contains(perfil)) {
				throw new RegraNegocioException("O perfil " + perfil + " não pode ser habilitado nesse vínculo.");
			}
		}
		boolean exigeRegistro = pedido.perfis().stream().anyMatch(PERFIS_COM_REGISTRO::contains);
		if (exigeRegistro && (pedido.registroProfissional() == null || pedido.registroProfissional().isBlank())) {
			throw new RegraNegocioException("Informe o registro profissional (CRM ou COREN).");
		}
		return instituicaoRepository.findById(pedido.instituicaoId())
				.orElseThrow(() -> new RegraNegocioException("Instituição não encontrada."));
	}

	private VinculoInstitucional novoVinculo(Usuario usuario, VinculoRequest pedido, LocalDateTime agora) {
		Instituicao instituicao = instituicaoRepository.getReferenceById(pedido.instituicaoId());
		VinculoInstitucional vinculo = new VinculoInstitucional();
		vinculo.setUsuario(usuario);
		vinculo.setInstituicao(instituicao);
		vinculo.setPerfisHabilitados(EnumSet.copyOf(pedido.perfis()));
		String registro = pedido.registroProfissional();
		vinculo.setRegistroProfissional(registro == null || registro.isBlank() ? null : registro.strip());
		vinculo.setInicioEm(agora);
		return vinculo;
	}

	private Usuario buscar(UUID id) {
		return usuarioRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
	}

	private UsuarioDetalheDTO detalhe(Usuario usuario) {
		return UsuarioDetalheDTO.de(usuario, vinculosPorUsuario(List.of(usuario)).getOrDefault(usuario.getId(), List.of()));
	}

	private Map<UUID, List<VinculoDoUsuarioDTO>> vinculosPorUsuario(List<Usuario> usuarios) {
		Map<UUID, List<VinculoDoUsuarioDTO>> resultado = new LinkedHashMap<>();
		if (usuarios.isEmpty()) {
			return resultado;
		}
		LocalDateTime agora = agora();
		vinculoRepository.buscarDosUsuarios(usuarios.stream().map(Usuario::getId).toList()).forEach(vinculo ->
				resultado.computeIfAbsent(vinculo.getUsuario().getId(), chave -> new ArrayList<>())
						.add(VinculoDoUsuarioDTO.de(vinculo, agora)));
		return resultado;
	}

	private void registrar(String loginDoAuditor, Usuario alvo, TipoDeAcaoAdministrativa tipo, String detalhe) {
		AcaoAdministrativa acao = new AcaoAdministrativa();
		acao.setAtor(usuarioRepository.findByLogin(loginDoAuditor)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado.")));
		acao.setAlvo(alvo);
		acao.setAcao(tipo);
		acao.setDetalhe(detalhe);
		acao.setOcorridoEm(agora());
		acaoRepository.save(acao);
	}

	private static String nomes(Set<Perfil> perfis) {
		return perfis.stream().map(Perfil::name).sorted().collect(Collectors.joining(", "));
	}

	private LocalDateTime agora() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
