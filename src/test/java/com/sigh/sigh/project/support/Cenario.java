package com.sigh.sigh.project.support;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.repository.PacienteRepository;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import com.sigh.sigh.project.usuario.repository.UsuarioRepository;
import com.sigh.sigh.project.usuario.repository.VinculoInstitucionalRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

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
	private final Clock clock;

	public Cenario(InstituicaoRepository instituicaoRepository, UsuarioRepository usuarioRepository,
			VinculoInstitucionalRepository vinculoRepository, PacienteRepository pacienteRepository, Clock clock) {
		this.instituicaoRepository = instituicaoRepository;
		this.usuarioRepository = usuarioRepository;
		this.vinculoRepository = vinculoRepository;
		this.pacienteRepository = pacienteRepository;
		this.clock = clock;
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
