package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.order.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    void add(Order order);

    /**
     * Grava a nova versão do pedido. Falha se outro processo alterou o pedido depois que ele foi
     * lido (controle de concorrência otimista pela coluna {@code version}).
     */
    void update(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByPixTxid(String txid);

    List<Order> findRecent(int limit);
}
