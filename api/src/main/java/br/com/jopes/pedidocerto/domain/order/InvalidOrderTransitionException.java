package br.com.jopes.pedidocerto.domain.order;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(OrderStatus from, String action) {
        super("Não é possível " + action + " um pedido com status " + from);
    }
}
