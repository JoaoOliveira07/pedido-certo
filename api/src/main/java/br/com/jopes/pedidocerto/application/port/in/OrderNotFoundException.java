package br.com.jopes.pedidocerto.application.port.in;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Object reference) {
        super("Pedido não encontrado: " + reference);
    }
}
