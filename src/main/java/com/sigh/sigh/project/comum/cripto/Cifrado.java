package com.sigh.sigh.project.comum.cripto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cifrado {

	String PENDENTE = "sigh:pendente";

	String gravadoEm();

	String legadoSe() default "";

	String legadoListadoEm() default "";
}
