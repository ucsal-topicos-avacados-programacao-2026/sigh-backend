package com.sigh.sigh.project.triagem.repository;

import com.sigh.sigh.project.triagem.entity.Triagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TriagemRepository extends JpaRepository<Triagem, UUID> {
}
