package com.sigh.sigh.project.instituicao.service;

import com.sigh.sigh.project.instituicao.dto.InstituicaoDaRedeDTO;
import com.sigh.sigh.project.instituicao.repository.InstituicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InstituicaoService {

	private static final Comparator<InstituicaoDaRedeDTO> ORDEM = Comparator
			.comparing(InstituicaoDaRedeDTO::nome, Collator.getInstance(Locale.of("pt", "BR")))
			.thenComparing(InstituicaoDaRedeDTO::id);

	private final InstituicaoRepository instituicaoRepository;

	@Transactional(readOnly = true)
	public List<InstituicaoDaRedeDTO> credenciadas() {
		return instituicaoRepository.findByCredenciadaTrue().stream()
				.map(instituicao -> new InstituicaoDaRedeDTO(instituicao.getId(), instituicao.getNome()))
				.sorted(ORDEM)
				.toList();
	}
}
