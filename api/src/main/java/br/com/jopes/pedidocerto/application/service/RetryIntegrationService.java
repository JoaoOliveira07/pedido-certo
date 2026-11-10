package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.OrderNotFoundException;
import br.com.jopes.pedidocerto.application.port.in.RetryIntegrationUseCase;
import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RetryIntegrationService implements RetryIntegrationUseCase {

    private final OrderRepository orders;
    private final OutboxStore outbox;
    private final TimelineRepository timeline;
    private final OrderEventsPublisher events;
    private final Clock clock;

    public RetryIntegrationService(OrderRepository orders, OutboxStore outbox, TimelineRepository timeline,
                                   OrderEventsPublisher events, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.timeline = timeline;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void retry(UUID orderId) {
        Instant now = clock.instant();
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        order.retryIntegration(now);
        orders.update(order);
        int requeued = outbox.requeueDead(orderId, now);
        timeline.append(new TimelineEntry(orderId, now, TimelineKind.REPROCESSADO,
                "Reprocessamento pedido manualmente: " + requeued + " mensagem(ns) de volta na fila, "
                        + "com as tentativas zeradas."));
        events.orderChanged(orderId, order.status());
    }
}
