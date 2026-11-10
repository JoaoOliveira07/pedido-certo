package br.com.jopes.pedidocerto.adapter.out.persistence;

import br.com.jopes.pedidocerto.application.port.out.IdempotencyStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code on conflict do nothing} sobre a chave primária: se outra transação inseriu a mesma
     * chave e ainda não terminou, o Postgres faz esta esperar. Quando a outra faz commit, esta não
     * insere nada; se a outra faz rollback, esta insere. Não há janela para dois pedidos.
     */
    @Override
    public boolean tryReserve(String key, String requestHash) {
        return jdbc.sql("""
                        insert into idempotency_keys (key, request_hash)
                        values (:key, :hash)
                        on conflict (key) do nothing
                        """)
                .param("key", key)
                .param("hash", requestHash)
                .update() == 1;
    }

    @Override
    public Optional<StoredKey> find(String key) {
        return jdbc.sql("select key, request_hash, order_id from idempotency_keys where key = :key")
                .param("key", key)
                .query((rs, rowNum) -> new StoredKey(
                        rs.getString("key"),
                        rs.getString("request_hash"),
                        rs.getObject("order_id", UUID.class)))
                .optional();
    }

    @Override
    public void attachOrder(String key, UUID orderId) {
        jdbc.sql("update idempotency_keys set order_id = :order where key = :key")
                .param("order", orderId)
                .param("key", key)
                .update();
    }
}
