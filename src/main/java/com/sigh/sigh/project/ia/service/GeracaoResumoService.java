package com.sigh.sigh.project.ia.service;

import com.sigh.sigh.project.comum.erro.IAIndisponivelException;
import com.sigh.sigh.project.ia.dto.CasoResumoDTO;
import com.sigh.sigh.project.ia.dto.ResumoHistoricoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Resumo do histórico por IA (RF-004), gerado em segundo plano: o pedido volta na hora com o resumo em
 * geração, e o médico consulta o resultado em {@code GET /resumos/{id}}. Assim, vários pedidos ao mesmo
 * tempo esperam na fila, em vez de estourarem o tempo-limite no serviço de IA.
 */
@Service
@RequiredArgsConstructor
public class GeracaoResumoService {

	private static final Logger log = LoggerFactory.getLogger(GeracaoResumoService.class);

	private final ResumoHistoricoService resumoHistoricoService;
	private final ClienteIA clienteIA;
	private final FilaDeResumosIA filaDeResumos;

	public ResumoHistoricoResponseDTO gerar(UUID atendimentoId, String loginMedico) {
		CasoResumoDTO caso = resumoHistoricoService.solicitar(atendimentoId, loginMedico);
		try {
			filaDeResumos.enfileirar(caso.resumoId(), () -> gerarEmSegundoPlano(caso));
		} catch (RejectedExecutionException filaCheia) {
			resumoHistoricoService.registrarFalha(caso.resumoId());
			throw new IAIndisponivelException(
					"Há muitos resumos na fila da IA. Tente de novo em instantes ou siga sem o resumo.");
		}
		return resumoHistoricoService.emGeracao(caso.resumoId());
	}

	/**
	 * Antes de mandar o histórico à IA, confere de novo a autorização: na fila, o paciente pode ter restringido
	 * um registro (RF-009) ou o médico pode ter perdido o vínculo. Uma falha deixa o resumo como falho; se nem
	 * isso puder ser gravado (banco fora), a consulta o mostra como falho depois, porque ele saiu da fila.
	 */
	void gerarEmSegundoPlano(CasoResumoDTO caso) {
		try {
			if (!resumoHistoricoService.continuaAutorizado(caso.resumoId())) {
				resumoHistoricoService.registrarFalha(caso.resumoId());
				return;
			}
			resumoHistoricoService.concluir(caso.resumoId(), clienteIA.resumirHistorico(caso.historico()));
		} catch (IAIndisponivelException falha) {
			resumoHistoricoService.registrarFalha(caso.resumoId());
		} catch (RuntimeException falha) {
			log.error("Falha inesperada ao gerar o resumo {}: {}", caso.resumoId(), falha.getClass().getSimpleName());
			resumoHistoricoService.registrarFalha(caso.resumoId());
		}
	}
}
