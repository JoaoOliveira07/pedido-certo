package br.com.jopes.pedidocerto.domain;

import br.com.jopes.pedidocerto.domain.order.InvalidOrderTransitionException;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import br.com.jopes.pedidocerto.domain.order.PaymentAmountMismatchException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private static Order newOrder() {
        return Order.place("Mercado Bom Preço", List.of(
                new OrderItem("LIN-5KG", "Linguiça toscana 5 kg", 3, 8_990),
                new OrderItem("SAL-3KG", "Salsicha 3 kg", 2, 4_550)), NOW);
    }

    @Test
    void totalIsTheSumOfTheItems() {
        assertEquals(3 * 8_990 + 2 * 4_550, newOrder().totalCents());
    }

    @Test
    void newOrderWaitsForPayment() {
        Order order = newOrder();
        assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, order.status());
        assertEquals(32, order.pixTxid().length());
    }

    @Test
    void rejectsEmptyOrTooLargeOrders() {
        assertThrows(IllegalArgumentException.class, () -> Order.place("Cliente", List.of(), NOW));
        List<OrderItem> tooMany = Collections.nCopies(Order.MAX_ITEMS + 1, new OrderItem("X", "X", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Order.place("Cliente", tooMany, NOW));
        assertThrows(IllegalArgumentException.class, () -> Order.place(" ", List.of(new OrderItem("X", "X", 1, 1)), NOW));
    }

    @Test
    void repeatedPaymentIsIgnored() {
        Order order = newOrder();
        assertTrue(order.markPaid(order.totalCents(), NOW));
        assertFalse(order.markPaid(order.totalCents(), NOW));
        assertEquals(OrderStatus.PAGO, order.status());
    }

    @Test
    void paymentWithWrongAmountIsRejected() {
        Order order = newOrder();
        assertThrows(PaymentAmountMismatchException.class, () -> order.markPaid(order.totalCents() - 1, NOW));
        assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, order.status());
    }

    @Test
    void cannotIntegrateBeforePayment() {
        Order order = newOrder();
        assertThrows(InvalidOrderTransitionException.class, () -> order.markIntegrated(1001, NOW));
    }

    @Test
    void repeatedErpConfirmationWithSameNumberIsIgnored() {
        Order order = newOrder();
        order.markPaid(order.totalCents(), NOW);
        assertTrue(order.markIntegrated(1001, NOW));
        assertFalse(order.markIntegrated(1001, NOW));
        assertEquals(Long.valueOf(1001), order.erpNumber());
    }

    @Test
    void erpConfirmationWithAnotherNumberIsABug() {
        Order order = newOrder();
        order.markPaid(order.totalCents(), NOW);
        order.markIntegrated(1001, NOW);
        assertThrows(IllegalStateException.class, () -> order.markIntegrated(1002, NOW));
    }

    @Test
    void failedIntegrationCanBeRetried() {
        Order order = newOrder();
        order.markPaid(order.totalCents(), NOW);
        order.markIntegrationFailed(NOW);
        assertEquals(OrderStatus.FALHA_INTEGRACAO, order.status());
        order.retryIntegration(NOW);
        assertEquals(OrderStatus.PAGO, order.status());
        assertThrows(InvalidOrderTransitionException.class, () -> order.retryIntegration(NOW));
    }
}
