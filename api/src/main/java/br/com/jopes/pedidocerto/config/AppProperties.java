package br.com.jopes.pedidocerto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app")
public record AppProperties(Relay relay, Chaos chaos, Cors cors) {

    /**
     * @param batchSize   mensagens por rodada do relay
     * @param lease       tempo de reserva de uma mensagem antes de outra instância poder pegá-la
     * @param maxAttempts tentativas antes de ir para a DLQ
     */
    public record Relay(int batchSize, Duration lease, int maxAttempts, Duration baseDelay, Duration maxDelay) {
    }

    /** Caos inicial da rede simulada; muda em tempo de execução pelo painel. */
    public record Chaos(double failureRate, double ackLossRate, double duplicateRate, int latencyMs) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
