package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.out.ErpGateway.ErpReceipt;
import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore.OutboxEntry;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.integration.RetryPolicy;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Registra, em transações curtas, o que aconteceu com cada tentativa de entrega ao ERP. */
@Component
public class DeliveryOutcomes {

    private final OutboxStore outbox;
    private final OrderRepository orders;
    private final TimelineRepository timeline;
    private final OrderEventsPublisher events;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public DeliveryOutcomes(OutboxStore outbox, OrderRepository orders, TimelineRepository timeline,
                            OrderEventsPublisher events, RetryPolicy retryPolicy, Clock clock) {
        this.outbox = outbox;
        this.orders = orders;
        this.timeline = timeline;
        this.events = events;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public void delivered(OutboxEntry entry, ErpReceipt receipt) {
        Instant now = clock.instant();
        outbox.markDelivered(entry.messageId(), now);

        Optional<Order> found = orders.findById(entry.event().orderId());
        if (found.isEmpty()) {
            return; // pedido apagado pela limpeza da demo
        }
        Order order = found.get();

        if (receipt.duplicatesDiscarded() > 0) {
            timeline.append(new TimelineEntry(order.id(), now, TimelineKind.DUPLICATA_DESCARTADA,
                    "O ERP já conhecia esta mensagem e descartou " + plural(receipt.duplicatesDiscarded(),
                            "entrega repetida", "entregas repetidas") + ". Nenhum pedido novo foi criado lá."));
        }
        if (order.markIntegrated(receipt.erpNumber(), now)) {
            orders.update(order);
            timeline.append(new TimelineEntry(order.id(), now, TimelineKind.INTEGRADO,
                    "Confirmado pelo ERP como pedido nº " + receipt.erpNumber()
                            + " (tentativa " + entry.attempts() + ")."));
        }
        events.orderChanged(order.id(), order.status());
    }

    @Transactional
    public void failed(OutboxEntry entry, String error) {
        Instant now = clock.instant();
        Optional<Order> found = orders.findById(entry.event().orderId());

        if (retryPolicy.exhausted(entry.attempts())) {
            outbox.markDead(entry.messageId(), error);
            found.filter(order -> order.status() == OrderStatus.PAGO).ifPresent(order -> {
                order.markIntegrationFailed(now);
                orders.update(order);
                timeline.append(new TimelineEntry(order.id(), now, TimelineKind.ENVIADO_PARA_DLQ,
                        entry.attempts() + " tentativas sem resposta do ERP. A mensagem foi para a fila de "
                                + "mensagens mortas e pode ser reprocessada."));
            });
        } else {
            Duration delay = retryPolicy.delayAfter(entry.attempts(), ThreadLocalRandom.current().nextDouble());
            outbox.scheduleRetry(entry.messageId(), now.plus(delay), error);
            found.ifPresent(order -> timeline.append(new TimelineEntry(order.id(), now,
                    TimelineKind.TENTATIVA_FALHOU, "Tentativa " + entry.attempts() + " falhou (" + error
                    + "). Nova tentativa em " + seconds(delay) + ".")));
        }
        found.ifPresent(order -> events.orderChanged(order.id(), order.status()));
    }

    private static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private static String seconds(Duration delay) {
        return String.format(java.util.Locale.ROOT, "%.1f s", delay.toMillis() / 1000.0).replace('.', ',');
    }
}
