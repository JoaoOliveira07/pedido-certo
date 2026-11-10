package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.order.OrderStatus;

import java.util.UUID;

/**
 * Avisa quem estiver assistindo (o painel) que um pedido mudou. Implementações devem publicar
 * só depois do commit, para ninguém ver um estado que ainda pode sofrer rollback.
 */
public interface OrderEventsPublisher {

    void orderChanged(UUID orderId, OrderStatus status);
}
