package com.sigh.sigh.project.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public class RelogioDeTeste extends Clock {

	public static final ZoneId FUSO = ZoneId.of("America/Bahia");

	private final AtomicReference<Instant> agora;

	public RelogioDeTeste(Instant inicio) {
		this.agora = new AtomicReference<>(inicio);
	}

	public static RelogioDeTeste em(LocalDateTime horaLocal) {
		return new RelogioDeTeste(horaLocal.atZone(FUSO).toInstant());
	}

	public void definir(Instant instante) {
		agora.set(instante);
	}

	public void avancar(Duration duracao) {
		agora.updateAndGet(instante -> instante.plus(duracao));
	}

	@Override
	public ZoneId getZone() {
		return FUSO;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Instant instant() {
		return agora.get();
	}
}
