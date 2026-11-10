package br.com.jopes.pedidocerto.adapter.out.chaos;

import br.com.jopes.pedidocerto.application.port.out.NetworkChaos;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuração de caos em memória. Com uma instância só, basta; com várias, cada uma teria a sua
 * (ver ADR 0005).
 */
public class InMemoryNetworkChaos implements NetworkChaos {

    private final AtomicReference<ChaosSettings> settings;

    public InMemoryNetworkChaos(ChaosSettings initial) {
        this.settings = new AtomicReference<>(Objects.requireNonNull(initial));
    }

    @Override
    public ChaosSettings current() {
        return settings.get();
    }

    @Override
    public void update(ChaosSettings next) {
        settings.set(Objects.requireNonNull(next));
    }
}
