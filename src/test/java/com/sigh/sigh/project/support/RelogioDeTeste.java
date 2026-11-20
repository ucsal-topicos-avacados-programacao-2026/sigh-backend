package com.sigh.sigh.project.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio controlado pelos testes, para que datas e horários sejam determinísticos. */
public class RelogioDeTeste extends Clock {

	private final AtomicReference<Instant> agora;

	public RelogioDeTeste(Instant inicio) {
		this.agora = new AtomicReference<>(inicio);
	}

	public void definir(Instant instante) {
		agora.set(instante);
	}

	public void avancar(Duration duracao) {
		agora.updateAndGet(instante -> instante.plus(duracao));
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
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
