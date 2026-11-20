package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.paciente.entity.Consentimento;
import com.sigh.sigh.project.paciente.entity.DecisaoConsentimento;
import com.sigh.sigh.project.paciente.entity.Paciente;
import com.sigh.sigh.project.paciente.entity.RestricaoAcesso;
import com.sigh.sigh.project.paciente.entity.TipoConsentimento;
import com.sigh.sigh.project.prontuario.entity.Prontuario;
import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.entity.TipoRegistro;
import com.sigh.sigh.project.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistroClinicoRepositoryTest {

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 19, 12, 0);

	@Autowired
	private RegistroClinicoRepository registroClinicoRepository;

	@Autowired
	private TestEntityManager banco;

	private Prontuario prontuario;
	private Usuario medico;
	private Instituicao instituicao;

	@BeforeEach
	public void prepararProntuario() {
		instituicao = new Instituicao();
		instituicao.setNome("Hospital Público Fictício");
		instituicao.setTipo(TipoInstituicao.PUBLICA);
		instituicao.setCredenciada(true);
		banco.persist(instituicao);
		medico = new Usuario();
		medico.setLogin("medico.teste");
		medico.setNome("Médico Fictício");
		medico.setSenhaHash("hash-ficticio");
		banco.persist(medico);
		Paciente paciente = new Paciente();
		paciente.setCpf("00000000001");
		paciente.setNome("Paciente Fictício");
		paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
		banco.persist(paciente);
		prontuario = new Prontuario();
		prontuario.setPaciente(paciente);
		prontuario.setCriadoEm(AGORA.minusYears(1));
		banco.persist(prontuario);
	}

	@Test
	void historicoNaoTrazRegistroRestritoPeloPaciente() {
		salvarRegistro("Asma", "Clínica geral", 3);
		RegistroClinico restrito = salvarRegistro("Depressão", "Saúde mental", 2);
		restringir(restrito, null, null);

		assertThat(conteudosDoHistorico()).containsExactly("Asma");
	}

	@Test
	void historicoNaoTrazCategoriaRestrita() {
		salvarRegistro("Asma", "Clínica geral", 3);
		salvarRegistro("Ansiedade", "Saúde mental", 2);
		restringir(null, "Saúde mental", null);

		assertThat(conteudosDoHistorico()).containsExactly("Asma");
	}

	@Test
	void restricaoEncerradaVoltaAPermitirORegistro() {
		RegistroClinico registro = salvarRegistro("Depressão", "Saúde mental", 2);
		restringir(registro, null, AGORA.minusDays(1));

		assertThat(conteudosDoHistorico()).containsExactly("Depressão");
	}

	@Test
	void historicoVemDoMaisRecenteParaOMaisAntigoRespeitandoOLimite() {
		salvarRegistro("Antigo", "Clínica geral", 30);
		salvarRegistro("Recente", "Clínica geral", 1);
		salvarRegistro("Intermediário", "Clínica geral", 10);

		List<RegistroClinico> historico = registroClinicoRepository
				.historicoVisivel(prontuario.getId(), instituicao.getId(), true, AGORA, Limit.of(2));

		assertThat(historico).extracting(RegistroClinico::getConteudo).containsExactly("Recente", "Intermediário");
	}

	@Test
	void categoriaRestritaValeSemDiferenciarMaiusculasAcentosEEspacos() {
		salvarRegistro("Asma", "Clínica geral", 3);
		salvarRegistro("Ansiedade", "Saúde mental", 2);
		restringir(null, "  saude   MENTAL ", null);

		assertThat(conteudosDoHistorico()).containsExactly("Asma");
		assertThat(registroClinicoRepository.contarRestritosOcultos(prontuario.getId(), instituicao.getId(), true, AGORA))
				.isEqualTo(1);
	}

	/** BE-14: sem consentimento amplo, a instituição continua vendo o que ela mesma registrou. */
	@Test
	void semCompartilhamentoAInstituicaoVeSoOsProprioRegistros() {
		Instituicao outra = novaInstituicao("Hospital Privado Fictício");
		salvarRegistro("Asma", "Clínica geral", 3);
		salvarRegistro("Fratura", "Ortopedia", 2, outra);

		assertThat(conteudos(instituicao, false)).containsExactly("Asma");
		assertThat(conteudos(outra, false)).containsExactly("Fratura");
		assertThat(conteudos(instituicao, true)).containsExactly("Fratura", "Asma");
	}

	@Test
	void autorizacaoAdicionalLiberaORestritoSoParaAInstituicaoAutorizada() {
		Instituicao outra = novaInstituicao("Hospital Privado Fictício");
		salvarRegistro("Asma", "Clínica geral", 3);
		salvarRegistro("Ansiedade", "Saúde mental", 2);
		restringir(null, "Saúde mental", null);
		Consentimento autorizacao = autorizar(instituicao, "saúde mental", null, 1);

		assertThat(conteudosDoHistorico()).containsExactly("Ansiedade", "Asma");
		assertThat(conteudos(outra, true)).containsExactly("Asma");
		assertThat(registroClinicoRepository.contarRestritosOcultos(prontuario.getId(), instituicao.getId(), true, AGORA))
				.isZero();

		revogar(autorizacao, 2);
		assertThat(conteudosDoHistorico()).containsExactly("Asma");
	}

	@Test
	void autorizacaoDeUmRegistroNaoLiberaOsOutrosDaCategoria() {
		RegistroClinico liberado = salvarRegistro("Ansiedade", "Saúde mental", 2);
		salvarRegistro("Depressão", "Saúde mental", 1);
		restringir(null, "Saúde mental", null);
		autorizar(instituicao, null, liberado, 1);

		assertThat(conteudosDoHistorico()).containsExactly("Ansiedade");
	}

	@Test
	void historicoCompletoERestritosIgnoramRestricaoEConsentimento() {
		Instituicao outra = novaInstituicao("Hospital Privado Fictício");
		salvarRegistro("Asma", "Clínica geral", 3);
		RegistroClinico restrito = salvarRegistro("Depressão", "Saúde mental", 2, outra);
		restringir(restrito, null, null);
		banco.flush();

		assertThat(registroClinicoRepository.historicoCompleto(prontuario.getId()))
				.extracting(RegistroClinico::getConteudo).containsExactly("Depressão", "Asma");
		assertThat(registroClinicoRepository.restritos(prontuario.getId(), AGORA)).containsExactly(restrito.getId());
	}

	private List<String> conteudosDoHistorico() {
		return conteudos(instituicao, true);
	}

	private List<String> conteudos(Instituicao vista, boolean compartilhado) {
		banco.flush();
		return registroClinicoRepository.historicoVisivel(prontuario.getId(), vista.getId(), compartilhado, AGORA,
						Limit.of(20))
				.stream().map(RegistroClinico::getConteudo).toList();
	}

	private Instituicao novaInstituicao(String nome) {
		Instituicao nova = new Instituicao();
		nova.setNome(nome);
		nova.setTipo(TipoInstituicao.PRIVADA);
		nova.setCredenciada(true);
		return banco.persist(nova);
	}

	private Consentimento autorizar(Instituicao destinataria, String categoria, RegistroClinico registro, int versao) {
		Consentimento autorizacao = new Consentimento();
		autorizacao.setPaciente(prontuario.getPaciente());
		autorizacao.setVersao(versao);
		autorizacao.setTipo(TipoConsentimento.ADICIONAL);
		autorizacao.setDecisao(DecisaoConsentimento.AUTORIZADO);
		autorizacao.setVigenteDesde(AGORA.minusDays(1));
		autorizacao.setDestinataria(destinataria);
		autorizacao.setCategoriaAlvo(categoria);
		autorizacao.setRegistroAlvo(registro);
		return banco.persist(autorizacao);
	}

	private void revogar(Consentimento autorizacao, int versao) {
		Consentimento revogacao = new Consentimento();
		revogacao.setPaciente(prontuario.getPaciente());
		revogacao.setVersao(versao);
		revogacao.setTipo(TipoConsentimento.ADICIONAL);
		revogacao.setDecisao(DecisaoConsentimento.REVOGADO);
		revogacao.setVigenteDesde(AGORA.minusHours(1));
		revogacao.setDestinataria(autorizacao.getDestinataria());
		revogacao.setCategoriaAlvo(autorizacao.getCategoriaAlvo());
		revogacao.setAnterior(autorizacao);
		banco.persist(revogacao);
	}

	private RegistroClinico salvarRegistro(String conteudo, String categoria, int diasAtras) {
		return salvarRegistro(conteudo, categoria, diasAtras, instituicao);
	}

	private RegistroClinico salvarRegistro(String conteudo, String categoria, int diasAtras, Instituicao origem) {
		RegistroClinico registro = new RegistroClinico();
		registro.setProntuario(prontuario);
		registro.setTipo(TipoRegistro.DIAGNOSTICO);
		registro.setCriadoEm(AGORA.minusDays(diasAtras));
		registro.setConteudo(conteudo);
		registro.setCategoriaInformacao(categoria);
		registro.setAutor(medico);
		registro.setOrigem(origem);
		return banco.persist(registro);
	}

	private void restringir(RegistroClinico registro, String categoria, LocalDateTime fimEm) {
		RestricaoAcesso restricao = new RestricaoAcesso();
		restricao.setProntuario(prontuario);
		restricao.setRegistroAlvo(registro);
		restricao.setCategoriaAlvo(categoria);
		restricao.setInicioEm(AGORA.minusDays(5));
		restricao.setFimEm(fimEm);
		restricao.setDefinidaPor(medico);
		banco.persist(restricao);
	}
}
