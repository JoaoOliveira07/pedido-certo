package br.com.jopes.pedidocerto.application.port.in;

public interface ConfirmPaymentUseCase {

    PaymentOutcome confirm(String pixTxid, long amountCents);

    enum PaymentOutcome {
        CONFIRMED,
        DUPLICATE_IGNORED
    }
}
