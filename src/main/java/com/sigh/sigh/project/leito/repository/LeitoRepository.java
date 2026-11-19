package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.Leito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeitoRepository extends JpaRepository<Leito, UUID> {
}
