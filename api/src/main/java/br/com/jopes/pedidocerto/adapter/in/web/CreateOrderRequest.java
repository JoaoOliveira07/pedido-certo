package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotEmpty @Size(max = Order.MAX_ITEMS) List<@Valid Item> items) {

    List<OrderItem> toDomain() {
        return items.stream()
                .map(item -> new OrderItem(item.sku(), item.description(), item.quantity(), item.unitPriceCents()))
                .toList();
    }

    public record Item(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 120) String description,
            @Min(1) @Max(999) int quantity,
            @Min(1) @Max(10_000_000) long unitPriceCents) {
    }
}
