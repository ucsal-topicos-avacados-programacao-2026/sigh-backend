package com.sigh.sigh.project.support;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RepresentacaoLegal;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.paciente.repository.RepresentacaoLegalRepository;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.prontuario.repository.ProntuarioRepository;
import com.sigh.sigh.project.prontuario.repository.RegistroClinicoRepository;
import com.sigh.sigh.project.triagem.dto.SinaisVitaisDTO;
import com.sigh.sigh.project.triagem.dto.TriagemRequestDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.service.TriagemService;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Dados fictícios para os testes. Cada chamada gera registros novos, sem depender de outros testes. */
@TestComponent
public class Cenario {

	private static final AtomicLong SEQUENCIA_CPF = new AtomicLong(100_000_000L);

	private final InstituicaoRepository instituicaoRepository;
	private final UsuarioRepository usuarioRepository;
	private final VinculoInstitucionalRepository vinculoRepository;
	private final PacienteRepository pacienteRepository;
	private final ProntuarioRepository prontuarioRepository;
	private final RegistroClinicoRepository registroClinicoRepository;
	private final RepresentacaoLegalRepository representacaoRepository;
	private final TriagemService triagemService;
	private final Clock clock;

	public Cenario(InstituicaoRepository instituicaoRepository, UsuarioRepository usuarioRepository,
			VinculoInstitucionalRepository vinculoRepository, PacienteRepository pacienteRepository,
			ProntuarioRepository prontuarioRepository, RegistroClinicoRepository registroClinicoRepository,
			RepresentacaoLegalRepository representacaoRepository, TriagemService triagemService, Clock clock) {
		this.instituicaoRepository = instituicaoRepository;
		this.usuarioRepository = usuarioRepository;
		this.vinculoRepository = vinculoRepository;
		this.pacienteRepository = pacienteRepository;
		this.prontuarioRepository = prontuarioRepository;
		this.registroClinicoRepository = registroClinicoRepository;
		this.representacaoRepository = representacaoRepository;
		this.triagemService = triagemService;
		this.clock = clock;
	}

	/** Triagem do atendimento com a classificação de risco confirmada, por um enfermeiro da instituição. */
	public UUID classificarRisco(UUID atendimentoId, Instituicao instituicao, CorRisco cor) {
		String enfermeiro = profissional(instituicao, Perfil.ENFERMEIRO);
		UUID triagem = triagemService.registrar(new TriagemRequestDTO(atendimentoId, "Dor torácica intensa",
				new SinaisVitaisDTO("90/60", 130, 28, new BigDecimal("36.5"), new BigDecimal("88.0"))), enfermeiro).id();
		triagemService.confirmarRisco(triagem, cor, enfermeiro);
		return triagem;
	}

	/** Profissional com vínculo ativo na instituição (BE-13); retorna o login. */
	public String profissional(Instituicao instituicao, Perfil perfil) {
		return vinculo(instituicao, perfil, LocalDateTime.now(clock).minusDays(30), null);
	}

	/** Registro clínico fictício no prontuário do paciente, com origem na instituição; retorna o id. */
	@Transactional
	public UUID registroClinico(UUID pacienteId, Instituicao origem, String categoria, String conteudo) {
		RegistroClinico registro = new RegistroClinico();
		registro.setProntuario(prontuarioRepository.findByPacienteId(pacienteId).orElseThrow());
		registro.setTipo(TipoRegistro.DIAGNOSTICO);
		registro.setCriadoEm(LocalDateTime.now(clock).minusHours(1));
		registro.setConteudo(conteudo);
		registro.setCategoriaInformacao(categoria);
		registro.setAutor(usuario(Perfil.MEDICO));
		registro.setOrigem(origem);
		return registroClinicoRepository.save(registro).getId();
	}

	/** Usuário com perfil Paciente que representa o paciente no período informado; retorna o login. */
	@Transactional
	public String representante(UUID pacienteId, LocalDateTime inicio, LocalDateTime fim) {
		Usuario usuario = usuario(Perfil.PACIENTE);
		RepresentacaoLegal representacao = new RepresentacaoLegal();
		representacao.setPaciente(pacienteRepository.findById(pacienteId).orElseThrow());
		representacao.setRepresentante(usuario);
		representacao.setDocumento("Termo de curatela fictício");
		representacao.setInicioEm(inicio);
		representacao.setFimEm(fim);
		representacaoRepository.save(representacao);
		return usuario.getLogin();
	}

	public Instituicao instituicao(boolean credenciada) {
		Instituicao instituicao = new Instituicao();
		instituicao.setNome("Hospital Fictício " + UUID.randomUUID());
		instituicao.setTipo(TipoInstituicao.PUBLICA);
		instituicao.setCredenciada(credenciada);
		return instituicaoRepository.save(instituicao);
	}

	/** Usuário com vínculo administrativo ativo na instituição; retorna o login. */
	public String administrativo(Instituicao instituicao) {
		LocalDateTime agora = LocalDateTime.now(clock);
		return vinculo(instituicao, Perfil.ADMINISTRATIVO, agora.minusDays(30), null);
	}

	public String vinculo(Instituicao instituicao, Perfil perfil, LocalDateTime inicio, LocalDateTime fim) {
		Usuario usuario = usuario(perfil);
		VinculoInstitucional vinculo = new VinculoInstitucional();
		vinculo.setUsuario(usuario);
		vinculo.setInstituicao(instituicao);
		vinculo.setPerfisHabilitados(new HashSet<>(Set.of(perfil)));
		vinculo.setInicioEm(inicio);
		vinculo.setFimEm(fim);
		vinculoRepository.save(vinculo);
		return usuario.getLogin();
	}

	/** Cria a conta de acesso do paciente e a vincula ao cadastro; retorna o login. */
	@Transactional
	public String contaDoPaciente(UUID pacienteId) {
		Usuario usuario = usuario(Perfil.PACIENTE);
		Paciente paciente = pacienteRepository.findById(pacienteId).orElseThrow();
		paciente.setConta(usuario);
		pacienteRepository.save(paciente);
		return usuario.getLogin();
	}

	public Usuario usuario(Perfil perfil) {
		Usuario usuario = new Usuario();
		usuario.setLogin("usuario-" + UUID.randomUUID());
		usuario.setNome("Usuário Fictício");
		usuario.setSenhaHash("hash-ficticio-de-teste");
		usuario.setMfaHabilitado(false);
		usuario.setPerfis(new HashSet<>(Set.of(perfil)));
		return usuarioRepository.save(usuario);
	}

	/** CPF fictício válido e diferente a cada chamada. */
	public static String novoCpf() {
		String base = String.valueOf(SEQUENCIA_CPF.incrementAndGet());
		int d1 = digito(base, 9);
		int d2 = digito(base + d1, 10);
		return base + d1 + d2;
	}

	public static String comMascara(String cpf) {
		return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
	}

	private static int digito(String digitos, int quantidade) {
		int soma = 0;
		for (int i = 0; i < quantidade; i++) {
			soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
		}
		int resto = soma % 11;
		return resto < 2 ? 0 : 11 - resto;
	}
}
