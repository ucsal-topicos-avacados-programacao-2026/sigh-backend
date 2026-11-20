package com.sigh.sigh.project.triagem.service;

import com.sigh.sigh.project.ia.service.ClienteIA;
import com.sigh.sigh.project.triagem.dto.CasoTriagemDTO;
import com.sigh.sigh.project.triagem.dto.TriagemResponseDTO;
import com.sigh.sigh.project.triagem.entity.CorRisco;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SugestaoRiscoService {

	private final ContextoTriagemService contextoTriagemService;
	private final ClienteIA clienteIA;
	private final TriagemService triagemService;

	public TriagemResponseDTO sugerirRisco(UUID triagemId, String loginEnfermeiro) {
		CasoTriagemDTO caso = contextoTriagemService.montarCaso(triagemId, loginEnfermeiro);
		CorRisco corSugerida = clienteIA.classificarRisco(caso.descricao());
		return triagemService.registrarSugestao(caso.triagemId(), corSugerida, caso.registrosUsados());
	}
}
