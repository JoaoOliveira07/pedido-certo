package br.com.jopes.pedidocerto.domain.integration;

import java.time.Duration;

/**
 * Backoff exponencial com teto e jitter. O jitter evita que muitas mensagens que falharam
 * juntas voltem todas no mesmo instante e derrubem o destino de novo.
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts deve ser >= 1");
        }
        if (baseDelay.isNegative() || baseDelay.isZero() || maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("atrasos inválidos");
        }
    }

    /** As tentativas já feitas esgotaram o limite? */
    public boolean exhausted(int attemptsMade) {
        return attemptsMade >= maxAttempts;
    }

    /**
     * Quanto esperar antes da próxima tentativa.
     *
     * @param attemptsMade tentativas já feitas (1 para a primeira falha)
     * @param jitter       número em [0, 1), sorteado por quem chama
     */
    public Duration delayAfter(int attemptsMade, double jitter) {
        if (jitter < 0 || jitter >= 1) {
            throw new IllegalArgumentException("jitter deve estar em [0, 1)");
        }
        int exponent = Math.min(Math.max(attemptsMade - 1, 0), 20);
        long exponential = baseDelay.toMillis() * (1L << exponent);
        long capped = Math.min(exponential, maxDelay.toMillis());
        // "equal jitter": metade fixa, metade aleatória
        long half = capped / 2;
        return Duration.ofMillis(half + Math.round(half * jitter));
    }
}
