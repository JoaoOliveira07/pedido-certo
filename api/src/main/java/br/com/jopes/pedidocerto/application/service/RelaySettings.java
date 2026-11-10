package br.com.jopes.pedidocerto.application.service;

import java.time.Duration;

/**
 * @param batchSize quantas mensagens o relay reserva por rodada
 * @param lease     por quanto tempo uma mensagem reservada fica bloqueada para outras instâncias
 */
public record RelaySettings(int batchSize, Duration lease) {

    public RelaySettings {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize deve ser >= 1");
        }
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease deve ser positivo");
        }
    }
}
