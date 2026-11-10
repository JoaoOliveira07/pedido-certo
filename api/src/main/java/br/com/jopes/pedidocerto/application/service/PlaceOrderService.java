package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.IdempotencyKeyReusedException;
import br.com.jopes.pedidocerto.application.port.in.OrderNotFoundException;
import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase;
import br.com.jopes.pedidocerto.application.port.out.IdempotencyStore;
import br.com.jopes.pedidocerto.application.port.out.IdempotencyStore.StoredKey;
import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orders;
    private final IdempotencyStore keys;
    private final TimelineRepository timeline;
    private final OrderEventsPublisher events;
    private final Clock clock;

    public PlaceOrderService(OrderRepository orders, IdempotencyStore keys, TimelineRepository timeline,
                             OrderEventsPublisher events, Clock clock) {
        this.orders = orders;
        this.keys = keys;
        this.timeline = timeline;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        Instant now = clock.instant();
        String hash = RequestFingerprint.of(command);

        if (!keys.tryReserve(command.idempotencyKey(), hash)) {
            return replay(command.idempotencyKey(), hash, now);
        }

        Order order = Order.place(command.customerName(), command.items(), now);
        orders.add(order);
        keys.attachOrder(command.idempotencyKey(), order.id());
        timeline.append(new TimelineEntry(order.id(), now, TimelineKind.PEDIDO_CRIADO,
                "Pedido criado. Aguardando PIX de " + Money.brl(order.totalCents()) + "."));
        events.orderChanged(order.id(), order.status());
        return new PlaceOrderResult(order, false);
    }

    private PlaceOrderResult replay(String key, String hash, Instant now) {
        StoredKey stored = keys.find(key)
                .orElseThrow(() -> new IllegalStateException("chave reservada sumiu: " + key));
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        if (stored.orderId() == null) {
            // tryReserve espera a transação concorrente terminar, então isto não deveria acontecer
            throw new IllegalStateException("chave sem pedido associado: " + key);
        }
        Order order = orders.findById(stored.orderId())
                .orElseThrow(() -> new OrderNotFoundException(stored.orderId()));
        timeline.append(new TimelineEntry(order.id(), now, TimelineKind.REQUISICAO_REPETIDA,
                "Mesma Idempotency-Key recebida de novo: devolvido o pedido original, nada foi criado."));
        events.orderChanged(order.id(), order.status());
        return new PlaceOrderResult(order, true);
    }
}
