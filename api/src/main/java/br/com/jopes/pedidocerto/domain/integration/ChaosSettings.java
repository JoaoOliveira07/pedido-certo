package br.com.jopes.pedidocerto.domain.integration;

/**
 * Quão ruim está a "rede" entre a loja e o ERP simulado.
 *
 * @param failureRate   chance de a mensagem nem chegar ao ERP (timeout)
 * @param ackLossRate   chance de o ERP processar, mas a resposta se perder no caminho
 * @param duplicateRate chance de a rede entregar a mesma mensagem duas vezes
 * @param latencyMs     atraso fixo de cada chamada ao ERP
 */
public record ChaosSettings(double failureRate, double ackLossRate, double duplicateRate, int latencyMs) {

    public static final int MAX_LATENCY_MS = 3_000;
    public static final ChaosSettings CALM = new ChaosSettings(0, 0, 0, 0);

    public ChaosSettings {
        requireRate(failureRate, "failureRate");
        requireRate(ackLossRate, "ackLossRate");
        requireRate(duplicateRate, "duplicateRate");
        if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) {
            throw new IllegalArgumentException("latencyMs deve estar entre 0 e " + MAX_LATENCY_MS);
        }
    }

    private static void requireRate(double value, String name) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " deve estar entre 0 e 1");
        }
    }
}
