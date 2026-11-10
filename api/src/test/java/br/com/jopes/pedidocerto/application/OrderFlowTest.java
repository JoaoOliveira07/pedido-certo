package br.com.jopes.pedidocerto.application;

import br.com.jopes.pedidocerto.application.InMemoryAdapters.MutableClock;
import br.com.jopes.pedidocerto.application.InMemoryAdapters.ScriptedErp;
import br.com.jopes.pedidocerto.application.InMemoryAdapters.ScriptedErp.Network;
import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase.PaymentOutcome;
import br.com.jopes.pedidocerto.application.port.in.IdempotencyKeyReusedException;
import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import br.com.jopes.pedidocerto.application.service.ConfirmPaymentService;
import br.com.jopes.pedidocerto.application.service.DeliveryOutcomes;
import br.com.jopes.pedidocerto.application.service.OutboxRelay;
import br.com.jopes.pedidocerto.application.service.PlaceOrderService;
import br.com.jopes.pedidocerto.application.service.RelaySettings;
import br.com.jopes.pedidocerto.application.service.RetryIntegrationService;
import br.com.jopes.pedidocerto.domain.integration.RetryPolicy;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O fluxo inteiro (pedido → PIX → outbox → ERP) com a rede se comportando mal de propósito. */
class OrderFlowTest {

    private static final List<OrderItem> ITEMS = List.of(new OrderItem("LIN-5KG", "Linguiça toscana 5 kg", 2, 8_990));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-10T12:00:00Z"));
    private final InMemoryAdapters.Orders orders = new InMemoryAdapters.Orders();
    private final InMemoryAdapters.Keys keys = new InMemoryAdapters.Keys();
    private final InMemoryAdapters.Outbox outbox = new InMemoryAdapters.Outbox();
    private final InMemoryAdapters.Timeline timeline = new InMemoryAdapters.Timeline();
    private final InMemoryAdapters.Events events = new InMemoryAdapters.Events();
    private final ScriptedErp erp = new ScriptedErp();
    private final RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(4));

    private final PlaceOrderService placeOrder = new PlaceOrderService(orders, keys, timeline, events, clock);
    private final ConfirmPaymentService confirmPayment =
            new ConfirmPaymentService(orders, outbox, timeline, events, clock);
    private final OutboxRelay relay = new OutboxRelay(outbox, erp,
            new DeliveryOutcomes(outbox, orders, timeline, events, retryPolicy, clock),
            new RelaySettings(10, Duration.ofSeconds(30)), clock);
    private final RetryIntegrationService retry =
            new RetryIntegrationService(orders, outbox, timeline, events, clock);

    @Test
    void sameIdempotencyKeyReturnsTheSameOrder() {
        PlaceOrderResult first = placeOrder.place(command("chave-0001", "Mercado A"));
        PlaceOrderResult second = placeOrder.place(command("chave-0001", "Mercado A"));

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.order().id(), second.order().id());
        assertEquals(1, orders.size());
    }

    @Test
    void sameIdempotencyKeyWithAnotherBodyIsRejected() {
        placeOrder.place(command("chave-0002", "Mercado A"));
        assertThrows(IdempotencyKeyReusedException.class, () -> placeOrder.place(command("chave-0002", "Mercado B")));
        assertEquals(1, orders.size());
    }

    @Test
    void repeatedWebhookWritesASingleOutboxMessage() {
        Order order = placeAndGet("chave-0003");

        assertEquals(PaymentOutcome.CONFIRMED, confirmPayment.confirm(order.pixTxid(), order.totalCents()));
        assertEquals(PaymentOutcome.DUPLICATE_IGNORED, confirmPayment.confirm(order.pixTxid(), order.totalCents()));

        assertEquals(1, outbox.messages.size());
        assertEquals(OrderStatus.PAGO, status(order));
    }

    @Test
    void happyPathIntegratesOnFirstAttempt() {
        Order order = paid("chave-0004");

        relay.deliverDue();

        assertEquals(OrderStatus.INTEGRADO, status(order));
        assertEquals(Long.valueOf(1001), orders.findById(order.id()).orElseThrow().erpNumber());
    }

    @Test
    void lostAckIsRetriedWithoutCreatingADuplicateInTheErp() {
        Order order = paid("chave-0005");
        erp.then(Network.ACK_LOST, Network.OK);

        relay.deliverDue();
        assertEquals(OrderStatus.PAGO, status(order), "sem ack, a loja ainda não sabe que o ERP recebeu");
        assertEquals(1, erp.ordersCreated(), "mas o ERP já criou o pedido");

        clock.advance(Duration.ofSeconds(10));
        relay.deliverDue();

        assertEquals(OrderStatus.INTEGRADO, status(order));
        assertEquals(1, erp.ordersCreated(), "a reentrega não criou outro pedido");
        assertEquals(1, erp.duplicatesDiscarded);
        assertTrue(kinds(order).contains(TimelineKind.DUPLICATA_DESCARTADA));
    }

    @Test
    void messageIsNotRetriedBeforeTheBackoff() {
        Order order = paid("chave-0006");
        erp.then(Network.TIMEOUT);

        relay.deliverDue();
        assertEquals(0, relay.deliverDue(), "ainda dentro do backoff");

        clock.advance(Duration.ofSeconds(5));
        assertEquals(1, relay.deliverDue());
        assertEquals(OrderStatus.INTEGRADO, status(order));
    }

    @Test
    void exhaustedAttemptsGoToTheDeadLetterQueueAndCanBeRetried() {
        Order order = paid("chave-0007");
        erp.always(Network.TIMEOUT);

        for (int attempt = 0; attempt < 3; attempt++) {
            relay.deliverDue();
            clock.advance(Duration.ofSeconds(10));
        }
        assertEquals(OrderStatus.FALHA_INTEGRACAO, status(order));
        assertEquals("DEAD", outbox.messages.values().iterator().next().status);
        assertTrue(kinds(order).contains(TimelineKind.ENVIADO_PARA_DLQ));

        erp.always(Network.OK);
        retry.retry(order.id());
        relay.deliverDue();

        assertEquals(OrderStatus.INTEGRADO, status(order));
        assertEquals(1, erp.ordersCreated());
    }

    @Test
    void leaseExpiryRedeliversAMessageWhoseResultWasNeverRecorded() {
        Order order = paid("chave-0008");
        // simula o processo morrendo depois de reservar a mensagem e antes de registrar o resultado
        outbox.claimDue(10, clock.instant(), Duration.ofSeconds(30));
        assertEquals(0, relay.deliverDue(), "reservada por outra instância");

        clock.advance(Duration.ofSeconds(31));
        relay.deliverDue();
        assertEquals(OrderStatus.INTEGRADO, status(order));
    }

    private Order paid(String key) {
        Order order = placeAndGet(key);
        confirmPayment.confirm(order.pixTxid(), order.totalCents());
        return order;
    }

    private Order placeAndGet(String key) {
        return placeOrder.place(command(key, "Mercado Bom Preço")).order();
    }

    private OrderStatus status(Order order) {
        return orders.findById(order.id()).orElseThrow().status();
    }

    private List<TimelineKind> kinds(Order order) {
        return timeline.findByOrder(order.id()).stream().map(e -> e.kind()).toList();
    }

    private static PlaceOrderCommand command(String key, String customer) {
        return new PlaceOrderCommand(key, customer, ITEMS);
    }
}
