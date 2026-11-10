package br.com.jopes.pedidocerto.domain.order;

import java.util.Objects;

public record OrderItem(String sku, String description, int quantity, long unitPriceCents) {

    public OrderItem {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(description, "description");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku não pode ser vazio");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        if (unitPriceCents <= 0) {
            throw new IllegalArgumentException("preço unitário deve ser positivo");
        }
    }

    public long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, (long) quantity);
    }
}
