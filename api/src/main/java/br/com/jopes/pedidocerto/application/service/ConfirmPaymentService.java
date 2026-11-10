package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase;
import br.com.jopes.pedidocerto.application.port.in.OrderNotFoundException;
import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.PaymentAmountMismatchException;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ConfirmPaymentService implements ConfirmPaymentUseCase {

    private final OrderRepository orders;
    private final OutboxStore outbox;
    private final TimelineRepository timeline;
    private final OrderEventsPublisher events;
    private final Clock clock;

    public ConfirmPaymentService(OrderRepository orders, OutboxStore outbox, TimelineRepository timeline,
                                 OrderEventsPublisher events, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.timeline = timeline;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Pedido pago e mensagem para o ERP são gravados na mesma transação (outbox). Se o processo
     * cair logo depois do commit, a mensagem já está salva e o relay vai entregá-la.
     */
    @Override
    @Transactional(noRollbackFor = PaymentAmountMismatchException.class)
    public PaymentOutcome confirm(String pixTxid, long amountCents) {
        Instant now = clock.instant();
        Order order = orders.findByPixTxid(pixTxid)
                .orElseThrow(() -> new OrderNotFoundException("txid " + pixTxid));

        boolean changed;
        try {
            changed = order.markPaid(amountCents, now);
        } catch (PaymentAmountMismatchException e) {
            timeline.append(new TimelineEntry(order.id(), now, TimelineKind.PAGAMENTO_RECUSADO,
                    "PIX de " + Money.brl(amountCents) + " recusado: o pedido custa "
                            + Money.brl(order.totalCents()) + "."));
            events.orderChanged(order.id(), order.status());
            throw e;
        }

        if (!changed) {
            timeline.append(new TimelineEntry(order.id(), now, TimelineKind.WEBHOOK_REPETIDO,
                    "Webhook do PIX chegou de novo e foi ignorado: o pedido já estava pago."));
            events.orderChanged(order.id(), order.status());
            return PaymentOutcome.DUPLICATE_IGNORED;
        }

        orders.update(order);
        outbox.enqueue(UUID.randomUUID(),
                new OrderPaidEvent(order.id(), order.customerName(), order.totalCents()), now);
        timeline.append(new TimelineEntry(order.id(), now, TimelineKind.PAGAMENTO_CONFIRMADO,
                "PIX confirmado. A mensagem para o ERP foi gravada no outbox na mesma transação."));
        events.orderChanged(order.id(), order.status());
        return PaymentOutcome.CONFIRMED;
    }
}
