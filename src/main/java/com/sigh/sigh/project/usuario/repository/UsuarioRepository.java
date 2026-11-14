package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
}
