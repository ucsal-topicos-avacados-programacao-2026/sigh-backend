package com.sigh.sigh.project.prontuario.service;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;

import java.util.List;
import java.util.stream.Collectors;

public final class DescricaoHistorico {

	private static final String SEPARADOR = " | ";

	private DescricaoHistorico() {
	}

	public static String descrever(List<RegistroClinico> historico) {
		return historico.stream()
				.map(DescricaoHistorico::descreverRegistro)
				.collect(Collectors.joining("\n"));
	}

	private static String descreverRegistro(RegistroClinico registro) {
		String cid = registro.getCodigoCID10() == null ? "" : " (CID " + registro.getCodigoCID10() + ")";
		return "- " + registro.getCriadoEm().toLocalDate()
				+ SEPARADOR + registro.getOrigem().getNome()
				+ SEPARADOR + registro.getTipo() + cid + ": " + registro.getConteudo();
	}
}
