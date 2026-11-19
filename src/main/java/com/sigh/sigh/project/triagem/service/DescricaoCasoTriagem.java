package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import com.sigh.sigh.project.triagem.entity.Triagem;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class DescricaoCasoTriagem {

	private static final String NAO_INFORMADO = "não informado";
	private static final String SEM_HISTORICO = "Histórico autorizado: nenhum. Classifique apenas pelos dados do momento.";

	private DescricaoCasoTriagem() {
	}

	static String montar(Triagem triagem, List<RegistroClinico> historico) {
		return String.join("\n",
				"Sintomas: " + triagem.getSintomas(),
				"Sinais vitais: " + descreverSinaisVitais(triagem.getSinaisVitais()),
				descreverHistorico(historico));
	}

	private static String descreverSinaisVitais(SinaisVitais sinais) {
		return String.join("; ",
				"pressão arterial " + valorOuNaoInformado(sinais.getPressaoArterial()),
				"frequência cardíaca " + valorOuNaoInformado(sinais.getFrequenciaCardiaca()),
				"frequência respiratória " + valorOuNaoInformado(sinais.getFrequenciaRespiratoria()),
				"temperatura " + valorOuNaoInformado(sinais.getTemperatura()),
				"saturação de oxigênio " + valorOuNaoInformado(sinais.getSaturacaoOxigenio()));
	}

	private static String descreverHistorico(List<RegistroClinico> historico) {
		if (historico.isEmpty()) {
			return SEM_HISTORICO;
		}
		return historico.stream()
				.map(DescricaoCasoTriagem::descreverRegistro)
				.collect(Collectors.joining("\n", "Histórico autorizado:\n", ""));
	}

	private static String descreverRegistro(RegistroClinico registro) {
		String cid = registro.getCodigoCID10() == null ? "" : " (CID " + registro.getCodigoCID10() + ")";
		return "- " + registro.getTipo() + cid + ": " + registro.getConteudo();
	}

	private static String valorOuNaoInformado(Object valor) {
		return Objects.toString(valor, NAO_INFORMADO);
	}
}
