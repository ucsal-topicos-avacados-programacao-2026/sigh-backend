package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import com.sigh.sigh.project.prontuario.service.DescricaoHistorico;
import com.sigh.sigh.project.triagem.entity.SinaisVitais;
import com.sigh.sigh.project.triagem.entity.Triagem;

import java.util.List;
import java.util.Objects;

final class DescricaoCasoTriagem {

	private static final String NAO_INFORMADO = "não informado";
	private static final String SEM_HISTORICO =
			"Histórico autorizado: nenhum. Classifique apenas pelos dados do momento.";

	private DescricaoCasoTriagem() {
	}

	static String montar(Triagem triagem, List<RegistroClinico> historico) {
		return String.join("\n",
				"Sintomas: " + triagem.getSintomas(),
				"Sinais vitais: " + descreverSinaisVitais(triagem.getSinaisVitais()),
				descreverHistorico(historico));
	}

	private static String descreverSinaisVitais(SinaisVitais sinais) {
		if (sinais == null) {
			return "ainda não medidos (classifique pelos sintomas e pelo histórico, sem supor valores)";
		}
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
		return "Histórico autorizado:\n" + DescricaoHistorico.descrever(historico);
	}

	private static String valorOuNaoInformado(Object valor) {
		return Objects.toString(valor, NAO_INFORMADO);
	}
}
