package com.sigh.sigh.project.comum.erro;

import com.sigh.sigh.project.comum.cripto.DadoClinicoIlegivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

/**
 * Respostas de erro em formato ProblemDetail (RFC 9457).
 * Nunca devolve stack trace, mensagens do banco ou os valores enviados (ex.: CPF).
 */
@RestControllerAdvice
public class TratadorDeErros {

	private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail validacao(MethodArgumentNotValidException ex) {
		Map<String, String> campos = new TreeMap<>();
		for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
			campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
		}
		return dadosInvalidos(campos);
	}

	@ExceptionHandler(DadosInvalidosException.class)
	public ProblemDetail dadosInvalidos(DadosInvalidosException ex) {
		return dadosInvalidos(ex.getCampos());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail corpoIlegivel(HttpMessageNotReadableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido.");
	}

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	public ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ProblemDetail regraNegocio(RegraNegocioException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	/**
	 * Só a violação de UNIQUE é conflito de operações simultâneas (409). Qualquer outra violação
	 * de integridade é erro interno (500). A mensagem do banco pode conter dados pessoais e não é
	 * repassada nem registrada; o log guarda apenas o SQLState.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail integridade(DataIntegrityViolationException ex) {
		if (ViolacaoDeUnicidade.eh(ex)) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"A operação conflita com outra feita ao mesmo tempo. Consulte novamente e repita.");
		}
		log.error("Violação de integridade inesperada (SQLState={}, constraint={})",
				ViolacaoDeUnicidade.sqlState(ex), ViolacaoDeUnicidade.restricao(ex));
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno.");
	}

	/**
	 * Dado clínico que não decifra (gravado fora da aplicação, copiado de outra linha ou adulterado): a
	 * leitura é recusada (RNF-004). O log já tem a coluna e a linha; a resposta não diz nada do dado.
	 */
	@ExceptionHandler(DadoClinicoIlegivelException.class)
	public ProblemDetail dadoClinicoIlegivel(DadoClinicoIlegivelException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Um dado clínico não passou na verificação de integridade e não foi exibido. O caso foi registrado.");
	}

	private static ProblemDetail dadosInvalidos(Map<String, String> campos) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
		problema.setProperty("campos", campos);
		return problema;
	}

	@ExceptionHandler(CredenciaisInvalidasException.class)
	public ProblemDetail credenciaisInvalidas(CredenciaisInvalidasException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(IAIndisponivelException.class)
	public ProblemDetail iaIndisponivel(IAIndisponivelException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}
}
