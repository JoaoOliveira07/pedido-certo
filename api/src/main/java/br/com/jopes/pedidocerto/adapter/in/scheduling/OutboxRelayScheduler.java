package br.com.jopes.pedidocerto.adapter.in.scheduling;

import br.com.jopes.pedidocerto.application.port.in.DeliverPendingUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Acorda o relay do outbox em intervalo fixo. Desligado nos testes para controlar cada rodada. */
@Component
@ConditionalOnProperty(name = "app.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler {

    private final DeliverPendingUseCase relay;

    OutboxRelayScheduler(DeliverPendingUseCase relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.relay.interval-ms:500}")
    void tick() {
        relay.deliverDue();
    }
}
