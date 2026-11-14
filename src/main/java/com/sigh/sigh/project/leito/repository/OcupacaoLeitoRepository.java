package com.sigh.sigh.project.leito.repository;

import com.sigh.sigh.project.leito.entity.OcupacaoLeito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OcupacaoLeitoRepository extends JpaRepository<OcupacaoLeito, UUID> {
}
