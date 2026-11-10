package br.com.jopes.pedidocerto.application.port.in;

/** A mesma Idempotency-Key chegou com um corpo diferente do original. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("A Idempotency-Key '" + key + "' já foi usada com outra requisição");
    }
}
