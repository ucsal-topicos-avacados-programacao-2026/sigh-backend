package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeracaoResumoService {

	private final ResumoHistoricoService resumoHistoricoService;
	private final ClienteIA clienteIA;

	public ResumoHistoricoResponseDTO gerar(UUID atendimentoId, String loginMedico) {
		CasoResumoDTO caso = resumoHistoricoService.solicitar(atendimentoId, loginMedico);
		try {
			String texto = clienteIA.resumirHistorico(caso.historico());
			return resumoHistoricoService.concluir(caso.resumoId(), texto);
		} catch (IAIndisponivelException falha) {
			resumoHistoricoService.registrarFalha(caso.resumoId());
			throw falha;
		}
	}
}
