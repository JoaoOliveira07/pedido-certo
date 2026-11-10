package br.com.jopes.pedidocerto.application.port.in;

import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;

import java.util.List;

public interface PlaceOrderUseCase {

    PlaceOrderResult place(PlaceOrderCommand command);

    record PlaceOrderCommand(String idempotencyKey, String customerName, List<OrderItem> items) {
    }

    /** @param replayed {@code true} quando a chave já tinha sido usada e o pedido original foi devolvido */
    record PlaceOrderResult(Order order, boolean replayed) {
    }
}
