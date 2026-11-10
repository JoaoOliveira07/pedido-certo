package br.com.jopes.pedidocerto.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Guarda as chaves {@code Idempotency-Key} já usadas, junto com o hash da requisição original,
 * para que uma repetição devolva o mesmo pedido em vez de criar outro.
 */
public interface IdempotencyStore {

    /**
     * Tenta reservar a chave. Se outra transação estiver reservando a mesma chave ao mesmo tempo,
     * esta espera aquela terminar.
     *
     * @return {@code true} se a chave era nova e agora pertence a esta requisição
     */
    boolean tryReserve(String key, String requestHash);

    Optional<StoredKey> find(String key);

    void attachOrder(String key, UUID orderId);

    record StoredKey(String key, String requestHash, UUID orderId) {
    }
}
