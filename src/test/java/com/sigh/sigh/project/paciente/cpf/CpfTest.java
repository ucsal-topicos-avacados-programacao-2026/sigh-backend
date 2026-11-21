package com.sigh.sigh.project.paciente.cpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

	@ParameterizedTest
	@ValueSource(strings = {"52998224725", "529.982.247-25", " 529.982.247-25 ", "11144477735", "00000000191"})
	void aceitaCpfValidoComOuSemMascara(String cpf) {
		assertThat(Cpf.ehValido(cpf)).isTrue();
	}

	@Test
	void normalizaParaOnzeDigitos() {
		assertThat(Cpf.normalizar("529.982.247-25")).isEqualTo("52998224725");
		assertThat(Cpf.normalizar(" 52998224725 ")).isEqualTo("52998224725");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"52998224724",
			"52998224715",
			"11111111111",
			"00000000000",
			"5299822472",
			"529982247250",
			"529.982.247/25",
			"529 982 247 25",
			"52998224a25",
			"   ",
			"5299822472５"
	})
	void rejeitaCpfInvalido(String cpf) {
		assertThat(Cpf.ehValido(cpf)).isFalse();
	}

	@Test
	void normalizarCpfInvalidoLancaExcecaoSemExporOValor() {
		assertThatThrownBy(() -> Cpf.normalizar("52998224724"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("CPF inválido");
	}
}
