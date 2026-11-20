package com.sigh.sigh.project.auditoria.repository;

import com.sigh.sigh.project.auditoria.dto.AcessoDoPacienteDTO;
import com.sigh.sigh.project.auditoria.dto.IndicadorInstituicaoDTO;
import com.sigh.sigh.project.auditoria.dto.RegistroAuditoriaResponseDTO;
import com.sigh.sigh.project.auditoria.entity.RegistroAuditoria;
import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.usuario.entity.Perfil;
import com.sigh.sigh.project.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistroAuditoriaRepositoryTest {

	private static final LocalDateTime DIA_DA_CONSULTA = LocalDateTime.of(2026, 11, 19, 10, 0);
	private static final String MENSAGEM_DO_BANCO = "não podem ser alterados nem excluídos";

	@Autowired
	private RegistroAuditoriaRepository registroAuditoriaRepository;

	@Autowired
	private TestEntityManager banco;

	private Instituicao publica;
	private Instituicao privada;
	private Usuario medico;
	private Prontuario prontuarioDaAna;
	private RegistroAuditoria acessoComum;

	@BeforeEach
	public void prepararAcessos() {
		publica = salvarInstituicao("Hospital Público Fictício", TipoInstituicao.PUBLICA);
		privada = salvarInstituicao("Hospital Privado Fictício", TipoInstituicao.PRIVADA);
		medico = salvarUsuario("medico.teste", "Médico Fictício");
		prontuarioDaAna = salvarProntuario("00000000001", "Ana Fictícia", salvarUsuario("ana.teste", "Ana Fictícia"));
		Prontuario prontuarioDoBruno = salvarProntuario("00000000002", "Bruno Fictício", null);
		acessoComum = salvarAcesso(prontuarioDaAna, publica, DIA_DA_CONSULTA, false);
		salvarAcesso(prontuarioDaAna, privada, DIA_DA_CONSULTA.plusHours(1), true);
		salvarAcesso(prontuarioDoBruno, publica, DIA_DA_CONSULTA.minusDays(10), false);
		banco.flush();
		banco.clear();
	}

	@Test
	void bancoBloqueiaAlteracaoDaAuditoria() {
		assertThatThrownBy(() -> banco.getEntityManager()
				.createNativeQuery("update registro_auditoria set finalidade_declarada = 'alterada' where id = :id")
				.setParameter("id", acessoComum.getId())
				.executeUpdate())
				.rootCause().hasMessageContaining(MENSAGEM_DO_BANCO);
	}

	@Test
	void bancoBloqueiaExclusaoDaAuditoria() {
		assertThatThrownBy(() -> banco.getEntityManager()
				.createNativeQuery("delete from registro_auditoria where id = :id")
				.setParameter("id", acessoComum.getId())
				.executeUpdate())
				.rootCause().hasMessageContaining(MENSAGEM_DO_BANCO);
	}

	@Test
	void pacienteVeSomenteOsAcessosAoProprioProntuario() {
		List<AcessoDoPacienteDTO> acessos = registroAuditoriaRepository
				.buscarAcessosDoPaciente("ana.teste", Pageable.unpaged()).getContent();

		assertThat(acessos).hasSize(2);
		assertThat(acessos.getFirst().instituicao()).isEqualTo("Hospital Privado Fictício");
		assertThat(acessos.getFirst().emergencia()).isTrue();
		assertThat(acessos).allMatch(acesso -> acesso.profissional().equals("Médico Fictício"));
	}

	@Test
	void auditorFiltraPorPeriodoEInstituicao() {
		List<RegistroAuditoriaResponseDTO> registros = registroAuditoriaRepository.buscarRegistros(
				publica.getId(), null, inicioDoDia(), inicioDoDiaSeguinte(), Pageable.unpaged()).getContent();

		assertThat(registros).extracting(RegistroAuditoriaResponseDTO::id).containsExactly(acessoComum.getId());
	}

	@Test
	void auditorFiltraSomenteEmergencias() {
		List<RegistroAuditoriaResponseDTO> registros = registroAuditoriaRepository.buscarRegistros(
				null, true, inicioDoDia(), inicioDoDiaSeguinte(), Pageable.unpaged()).getContent();

		assertThat(registros).singleElement().satisfies(registro -> {
			assertThat(registro.instituicao()).isEqualTo("Hospital Privado Fictício");
			assertThat(registro.justificativa()).isNotBlank();
		});
	}

	@Test
	void indicadoresContamAcessosEEmergenciasPorInstituicao() {
		List<IndicadorInstituicaoDTO> indicadores = registroAuditoriaRepository
				.contarAcessosPorInstituicao(inicioDoDia(), inicioDoDiaSeguinte());

		assertThat(indicadores).containsExactlyInAnyOrder(
				new IndicadorInstituicaoDTO(publica.getId(), publica.getNome(), 1, 0),
				new IndicadorInstituicaoDTO(privada.getId(), privada.getNome(), 1, 1));
	}

	private LocalDateTime inicioDoDia() {
		return DIA_DA_CONSULTA.toLocalDate().atStartOfDay();
	}

	private LocalDateTime inicioDoDiaSeguinte() {
		return inicioDoDia().plusDays(1);
	}

	private Instituicao salvarInstituicao(String nome, TipoInstituicao tipo) {
		Instituicao instituicao = new Instituicao();
		instituicao.setNome(nome);
		instituicao.setTipo(tipo);
		instituicao.setCredenciada(true);
		return banco.persist(instituicao);
	}

	private Usuario salvarUsuario(String login, String nome) {
		Usuario usuario = new Usuario();
		usuario.setLogin(login);
		usuario.setNome(nome);
		usuario.setSenhaHash("hash-ficticio");
		return banco.persist(usuario);
	}

	private Prontuario salvarProntuario(String cpf, String nome, Usuario conta) {
		Paciente paciente = new Paciente();
		paciente.setCpf(cpf);
		paciente.setNome(nome);
		paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
		paciente.setConta(conta);
		banco.persist(paciente);
		Prontuario prontuario = new Prontuario();
		prontuario.setPaciente(paciente);
		prontuario.setCriadoEm(DIA_DA_CONSULTA.minusYears(1));
		return banco.persist(prontuario);
	}

	private RegistroAuditoria salvarAcesso(Prontuario prontuario, Instituicao instituicao, LocalDateTime ocorridoEm,
			boolean emergencia) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setProntuario(prontuario);
		registro.setAtor(medico);
		registro.setPerfil(Perfil.MEDICO);
		registro.setInstituicao(instituicao);
		registro.setOcorridoEm(ocorridoEm);
		registro.setFinalidadeDeclarada("Atendimento fictício");
		registro.setEmergencia(emergencia);
		registro.setJustificativa(emergencia ? "Paciente inconsciente, risco iminente" : null);
		return banco.persist(registro);
	}
}
