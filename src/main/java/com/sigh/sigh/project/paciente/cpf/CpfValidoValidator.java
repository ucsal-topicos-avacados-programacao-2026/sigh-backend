package com.sigh.sigh.project.paciente.cpf;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidoValidator implements ConstraintValidator<CpfValido, String> {

	@Override
	public boolean isValid(String valor, ConstraintValidatorContext context) {
		// Campo ausente é tratado por @NotBlank
		return valor == null || Cpf.ehValido(valor);
	}
}
