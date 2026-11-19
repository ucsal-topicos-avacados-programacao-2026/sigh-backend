package com.sigh.sigh.project.instituicao.repository;

import com.sigh.sigh.project.instituicao.entity.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstituicaoRepository extends JpaRepository<Instituicao, UUID> {
}
