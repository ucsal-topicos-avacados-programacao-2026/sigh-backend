package com.sigh.sigh.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Habilita @PreAuthorize nos endpoints. Os papéis esperados seguem o enum Perfil
 * (ROLE_ADMINISTRATIVO, ROLE_PACIENTE, ...) e o nome do usuário autenticado é o login.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class MethodSecurityConfig {
}
