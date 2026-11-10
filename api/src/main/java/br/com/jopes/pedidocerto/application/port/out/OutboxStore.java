package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox transacional: a mensagem para o ERP é gravada na mesma transação que muda o pedido,
 * então nunca existe pedido pago sem mensagem, nem mensagem sem pedido pago.
 */
public interface OutboxStore {

    void enqueue(UUID messageId, OrderPaidEvent event, Instant now);

    /**
     * Reserva até {@code limit} mensagens prontas para envio e conta mais uma tentativa em cada.
     * A reserva expira depois de {@code lease}; se o processo morrer no meio do envio, outra
     * instância pega a mensagem de volta. Mensagens reservadas por outra instância são puladas.
     */
    List<OutboxEntry> claimDue(int limit, Instant now, Duration lease);

    void markDelivered(UUID messageId, Instant now);

    void scheduleRetry(UUID messageId, Instant nextAttemptAt, String error);

    void markDead(UUID messageId, String error);

    /** Devolve para a fila as mensagens mortas de um pedido, zerando as tentativas. */
    int requeueDead(UUID orderId, Instant now);

    record OutboxEntry(UUID messageId, OrderPaidEvent event, int attempts) {
    }
}
