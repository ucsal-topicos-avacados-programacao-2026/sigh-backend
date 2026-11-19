package com.sigh.sigh.project.triagem.repository;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.prontuario.entity.Atendimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.StatusAtendimento;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import com.sigh.sigh.project.triagem.entity.Triagem;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TriagemRepositoryTest {

	private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 19, 8, 0);

	@Autowired
	private TriagemRepository triagemRepository;

	@Autowired
	private TestEntityManager banco;

	private Instituicao instituicao;
	private Usuario enfermeiro;
	private int pacientesCriados;

	@BeforeEach
	public void prepararCadastros() {
		instituicao = new Instituicao();
		instituicao.setNome("Hospital Público Fictício");
		instituicao.setTipo(TipoInstituicao.PUBLICA);
		instituicao.setCredenciada(true);
		banco.persist(instituicao);
		enfermeiro = new Usuario();
		enfermeiro.setLogin("enfermeira.teste");
		enfermeiro.setNome("Enfermeira Fictícia");
		enfermeiro.setSenhaHash("hash-ficticio");
		banco.persist(enfermeiro);
	}

	@Test
	void filaOrdenaPelaCorConfirmadaEDepoisPelaChegada() {
		registrarTriagem("Paciente Verde", INICIO, CorRisco.VERDE);
		registrarTriagem("Paciente Amarelo Tardio", INICIO.plusMinutes(30), CorRisco.AMARELO);
		registrarTriagem("Paciente Vermelho", INICIO.plusMinutes(40), CorRisco.VERMELHO);
		registrarTriagem("Paciente Amarelo Cedo", INICIO.plusMinutes(10), CorRisco.AMARELO);

		List<String> fila = nomesNaFila();

		assertThat(fila).containsExactly(
				"Paciente Vermelho", "Paciente Amarelo Cedo", "Paciente Amarelo Tardio", "Paciente Verde");
	}

	@Test
	void triagemSemConfirmacaoNaoEntraNaFila() {
		registrarTriagem("Paciente Confirmado", INICIO, CorRisco.AZUL);
		registrarTriagem("Paciente Sem Confirmacao", INICIO, null);

		assertThat(nomesNaFila()).containsExactly("Paciente Confirmado");
	}

	@Test
	void pacienteRetriadoApareceUmaVezComACorMaisRecente() {
		Atendimento atendimento = registrarTriagem("Paciente Que Piorou", INICIO, CorRisco.VERDE);
		confirmarNovaTriagem(atendimento, CorRisco.LARANJA, INICIO.plusMinutes(50));
		banco.flush();
		banco.clear();

		List<Triagem> fila = triagemRepository.buscarFila(instituicao.getId(), Pageable.unpaged()).getContent();

		assertThat(fila).singleElement().extracting(Triagem::getCorConfirmada).isEqualTo(CorRisco.LARANJA);
	}

	private List<String> nomesNaFila() {
		banco.flush();
		banco.clear();
		return triagemRepository.buscarFila(instituicao.getId(), Pageable.unpaged()).stream()
				.map(triagem -> triagem.getAtendimento().getProntuario().getPaciente().getNome())
				.toList();
	}

	private Atendimento registrarTriagem(String nomePaciente, LocalDateTime chegadaEm, CorRisco corConfirmada) {
		Atendimento atendimento = new Atendimento();
		atendimento.setProntuario(criarProntuario(nomePaciente));
		atendimento.setInstituicao(instituicao);
		atendimento.setChegadaEm(chegadaEm);
		atendimento.setMotivo("Motivo fictício");
		atendimento.setStatus(StatusAtendimento.EM_TRIAGEM);
		banco.persist(atendimento);
		Triagem triagem = new Triagem();
		triagem.setAtendimento(atendimento);
		triagem.setSintomas("Sintomas fictícios");
		triagem.setSinaisVitais(new SinaisVitais());
		triagem.setRegistrador(enfermeiro);
		triagem.setCorConfirmada(corConfirmada);
		triagem.setConfirmadaEm(corConfirmada == null ? null : chegadaEm.plusMinutes(5));
		banco.persist(triagem);
		return atendimento;
	}

	private void confirmarNovaTriagem(Atendimento atendimento, CorRisco cor, LocalDateTime confirmadaEm) {
		Triagem novaTriagem = new Triagem();
		novaTriagem.setAtendimento(atendimento);
		novaTriagem.setSintomas("Piora dos sintomas");
		novaTriagem.setSinaisVitais(new SinaisVitais());
		novaTriagem.setRegistrador(enfermeiro);
		novaTriagem.setCorConfirmada(cor);
		novaTriagem.setConfirmadaEm(confirmadaEm);
		banco.persist(novaTriagem);
	}

	private Prontuario criarProntuario(String nomePaciente) {
		pacientesCriados++;
		Paciente paciente = new Paciente();
		paciente.setCpf("%011d".formatted(pacientesCriados));
		paciente.setNome(nomePaciente);
		paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
		banco.persist(paciente);
		Prontuario prontuario = new Prontuario();
		prontuario.setPaciente(paciente);
		prontuario.setCriadoEm(INICIO);
		banco.persist(prontuario);
		return prontuario;
	}
}
