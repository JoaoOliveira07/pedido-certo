package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, String customerName, OrderStatus status, long totalCents, String pixTxid,
                     Long erpNumber, Instant createdAt, Instant updatedAt, List<Item> items) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.customerName(), order.status(), order.totalCents(),
                order.pixTxid(), order.erpNumber(), order.createdAt(), order.updatedAt(),
                order.items().stream().map(Item::from).toList());
    }

    public record Item(String sku, String description, int quantity, long unitPriceCents) {
        static Item from(OrderItem item) {
            return new Item(item.sku(), item.description(), item.quantity(), item.unitPriceCents());
        }
    }
}
