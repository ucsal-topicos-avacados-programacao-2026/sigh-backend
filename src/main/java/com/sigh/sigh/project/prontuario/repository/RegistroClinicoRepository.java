package com.sigh.sigh.project.prontuario.repository;

import com.sigh.sigh.project.prontuario.entity.RegistroClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistroClinicoRepository extends JpaRepository<RegistroClinico, UUID> {
}
