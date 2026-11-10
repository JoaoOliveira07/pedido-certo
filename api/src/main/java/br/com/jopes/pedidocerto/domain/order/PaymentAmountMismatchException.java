package br.com.jopes.pedidocerto.domain.order;

public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(long expectedCents, long receivedCents) {
        super("Valor pago (" + receivedCents + ") difere do total do pedido (" + expectedCents + ")");
    }
}
