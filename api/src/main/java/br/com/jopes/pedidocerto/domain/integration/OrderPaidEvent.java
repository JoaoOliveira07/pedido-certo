package br.com.jopes.pedidocerto.domain.integration;

import java.util.UUID;

/** Mensagem que vai para o ERP quando um pedido é pago. É o conteúdo gravado no outbox. */
public record OrderPaidEvent(UUID orderId, String customerName, long totalCents) {
}
