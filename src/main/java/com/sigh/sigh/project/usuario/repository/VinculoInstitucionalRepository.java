package com.sigh.sigh.project.usuario.repository;

import com.sigh.sigh.project.usuario.entity.VinculoInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VinculoInstitucionalRepository extends JpaRepository<VinculoInstitucional, UUID> {
}
