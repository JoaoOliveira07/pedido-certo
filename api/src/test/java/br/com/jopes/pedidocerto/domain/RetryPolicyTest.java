package br.com.jopes.pedidocerto.domain;

import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import br.com.jopes.pedidocerto.domain.integration.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(8));

    @Test
    void delayDoublesUntilTheCap() {
        // jitter 0 devolve metade do atraso exponencial
        assertEquals(Duration.ofMillis(500), policy.delayAfter(1, 0));
        assertEquals(Duration.ofMillis(1000), policy.delayAfter(2, 0));
        assertEquals(Duration.ofMillis(2000), policy.delayAfter(3, 0));
        assertEquals(Duration.ofMillis(4000), policy.delayAfter(4, 0));
        assertEquals(Duration.ofMillis(4000), policy.delayAfter(30, 0));
    }

    @Test
    void jitterStaysWithinTheCap() {
        Duration max = policy.delayAfter(10, 0.999);
        assertTrue(max.compareTo(Duration.ofSeconds(8)) <= 0);
        assertTrue(max.compareTo(Duration.ofSeconds(4)) > 0);
        assertThrows(IllegalArgumentException.class, () -> policy.delayAfter(1, 1.0));
    }

    @Test
    void exhaustedAfterMaxAttempts() {
        assertFalse(policy.exhausted(4));
        assertTrue(policy.exhausted(5));
    }

    @Test
    void chaosRatesMustBeProbabilities() {
        assertThrows(IllegalArgumentException.class, () -> new ChaosSettings(1.5, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChaosSettings(0, -0.1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChaosSettings(0, 0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChaosSettings(0, 0, 0, ChaosSettings.MAX_LATENCY_MS + 1));
    }
}
