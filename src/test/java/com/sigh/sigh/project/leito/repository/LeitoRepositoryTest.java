package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import com.sigh.sigh.project.instituicao.entity.TipoInstituicao;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import com.sigh.sigh.project.leito.entity.CategoriaLeito;
import com.sigh.sigh.project.leito.entity.Leito;
import com.sigh.sigh.project.leito.entity.StatusLeito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeitoRepositoryTest {

	@Autowired
	private LeitoRepository leitoRepository;

	@Autowired
	private InstituicaoRepository instituicaoRepository;

	private Instituicao publica;
	private Instituicao privada;

	@BeforeEach
	public void prepararLeitos() {
		publica = salvarInstituicao("Hospital Público Fictício", TipoInstituicao.PUBLICA);
		privada = salvarInstituicao("Hospital Privado Fictício", TipoInstituicao.PRIVADA);
		salvarLeito(publica, "UTI-01", CategoriaLeito.UTI, StatusLeito.LIVRE);
		salvarLeito(publica, "UTI-02", CategoriaLeito.UTI, StatusLeito.OCUPADO);
		salvarLeito(privada, "UTI-01", CategoriaLeito.UTI, StatusLeito.LIVRE);
		salvarLeito(privada, "PED-01", CategoriaLeito.PEDIATRICO, StatusLeito.LIVRE);
	}

	@Test
	void buscaLeitosLivresDeUmaCategoriaEmTodaARede() {
		Page<Leito> leitos = leitoRepository.buscar(null, CategoriaLeito.UTI, StatusLeito.LIVRE, Pageable.unpaged());

		assertThat(leitos).extracting(leito -> leito.getInstituicao().getId())
				.containsExactlyInAnyOrder(publica.getId(), privada.getId());
	}

	@Test
	void buscaTodosOsLeitosDeUmaInstituicao() {
		Page<Leito> leitos = leitoRepository.buscar(publica.getId(), null, null, Pageable.unpaged());

		assertThat(leitos).extracting(Leito::getCodigo).containsExactlyInAnyOrder("UTI-01", "UTI-02");
	}

	@Test
	void codigoEhUnicoSomenteDentroDaInstituicao() {
		assertThat(leitoRepository.existsByInstituicaoIdAndCodigo(publica.getId(), "UTI-01")).isTrue();
		assertThat(leitoRepository.existsByInstituicaoIdAndCodigo(privada.getId(), "UTI-02")).isFalse();
	}

	private Instituicao salvarInstituicao(String nome, TipoInstituicao tipo) {
		Instituicao instituicao = new Instituicao();
		instituicao.setNome(nome);
		instituicao.setTipo(tipo);
		instituicao.setCredenciada(true);
		return instituicaoRepository.save(instituicao);
	}

	private void salvarLeito(Instituicao instituicao, String codigo, CategoriaLeito categoria, StatusLeito status) {
		Leito leito = new Leito();
		leito.setInstituicao(instituicao);
		leito.setCodigo(codigo);
		leito.setSetor("Setor fictício");
		leito.setCategoria(categoria);
		leito.setStatus(status);
		leitoRepository.save(leito);
	}
}
