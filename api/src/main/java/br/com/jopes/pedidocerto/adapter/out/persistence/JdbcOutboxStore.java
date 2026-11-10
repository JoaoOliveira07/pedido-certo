package br.com.jopes.pedidocerto.adapter.out.persistence;

import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.ts;

/**
 * Outbox e fila ao mesmo tempo, no próprio Postgres. {@code FOR UPDATE SKIP LOCKED} deixa várias
 * instâncias do relay trabalharem juntas sem pegar a mesma mensagem. Ver ADR 0003.
 */
@Repository
class JdbcOutboxStore implements OutboxStore {

    private static final String TYPE_ORDER_PAID = "OrderPaid";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcOutboxStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void enqueue(UUID messageId, OrderPaidEvent event, Instant now) {
        jdbc.sql("""
                        insert into outbox (id, aggregate_id, type, payload, status, next_attempt_at, created_at)
                        values (:id, :aggregate, :type, cast(:payload as jsonb), 'PENDING', :now, :now)
                        """)
                .param("id", messageId)
                .param("aggregate", event.orderId())
                .param("type", TYPE_ORDER_PAID)
                .param("payload", write(event))
                .param("now", ts(now))
                .update();
    }

    @Override
    public List<OutboxEntry> claimDue(int limit, Instant now, Duration lease) {
        return jdbc.sql("""
                        update outbox o
                           set status = 'IN_FLIGHT', locked_until = :lease, attempts = o.attempts + 1
                         where o.id in (select id
                                          from outbox
                                         where (status = 'PENDING' and next_attempt_at <= :now)
                                            or (status = 'IN_FLIGHT' and locked_until < :now)
                                         order by next_attempt_at
                                         limit :limit
                                           for update skip locked)
                        returning o.id, o.payload, o.attempts
                        """)
                .param("lease", ts(now.plus(lease)))
                .param("now", ts(now))
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxEntry(
                        rs.getObject("id", UUID.class),
                        read(rs.getString("payload")),
                        rs.getInt("attempts")))
                .list();
    }

    @Override
    public void markDelivered(UUID messageId, Instant now) {
        jdbc.sql("""
                        update outbox
                           set status = 'DELIVERED', delivered_at = :now, locked_until = null, last_error = null
                         where id = :id
                        """)
                .param("now", ts(now))
                .param("id", messageId)
                .update();
    }

    @Override
    public void scheduleRetry(UUID messageId, Instant nextAttemptAt, String error) {
        jdbc.sql("""
                        update outbox
                           set status = 'PENDING', next_attempt_at = :next, locked_until = null, last_error = :error
                         where id = :id
                        """)
                .param("next", ts(nextAttemptAt))
                .param("error", error)
                .param("id", messageId)
                .update();
    }

    @Override
    public void markDead(UUID messageId, String error) {
        jdbc.sql("update outbox set status = 'DEAD', locked_until = null, last_error = :error where id = :id")
                .param("error", error)
                .param("id", messageId)
                .update();
    }

    @Override
    public int requeueDead(UUID orderId, Instant now) {
        return jdbc.sql("""
                        update outbox
                           set status = 'PENDING', attempts = 0, next_attempt_at = :now, last_error = null
                         where aggregate_id = :order and status = 'DEAD'
                        """)
                .param("now", ts(now))
                .param("order", orderId)
                .update();
    }

    private String write(OrderPaidEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("não foi possível serializar o evento", e);
        }
    }

    private OrderPaidEvent read(String payload) {
        try {
            return json.readValue(payload, OrderPaidEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("payload inválido no outbox: " + payload, e);
        }
    }
}
