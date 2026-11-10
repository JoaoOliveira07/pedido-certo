package br.com.jopes.pedidocerto.adapter.out.erp;

import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * O "ERP": outro sistema, com o próprio schema e a própria transação. Na vida real estaria
 * do outro lado de uma rede; aqui fica no mesmo processo para a demo caber numa VPS pequena.
 *
 * <p>É um consumidor idempotente: guarda o id de cada mensagem recebida (inbox) e, se a mesma
 * mensagem chegar de novo, devolve o número já criado em vez de criar outro pedido.
 */
@Component
class SimulatedErp {

    private final JdbcClient jdbc;

    SimulatedErp(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reception receive(UUID messageId, OrderPaidEvent event) {
        Optional<Long> known = jdbc.sql("select erp_number from erp.inbox where message_id = :id")
                .param("id", messageId)
                .query(Long.class)
                .optional();
        if (known.isPresent()) {
            countDuplicate(messageId);
            return new Reception(known.get(), true);
        }

        // unique(order_id) é a segunda linha de defesa: mesmo com outra mensagem, um pedido só
        Optional<Long> created = jdbc.sql("""
                        insert into erp.orders (order_id, total_cents)
                        values (:order, :total)
                        on conflict (order_id) do nothing
                        returning erp_number
                        """)
                .param("order", event.orderId())
                .param("total", event.totalCents())
                .query(Long.class)
                .optional();
        long erpNumber = created.orElseGet(() -> jdbc.sql("select erp_number from erp.orders where order_id = :order")
                .param("order", event.orderId())
                .query(Long.class)
                .single());

        int inserted = jdbc.sql("""
                        insert into erp.inbox (message_id, erp_number)
                        values (:id, :erp)
                        on conflict (message_id) do nothing
                        """)
                .param("id", messageId)
                .param("erp", erpNumber)
                .update();

        boolean duplicate = created.isEmpty() || inserted == 0;
        if (duplicate) {
            countDuplicate(messageId);
        }
        return new Reception(erpNumber, duplicate);
    }

    private void countDuplicate(UUID messageId) {
        jdbc.sql("update erp.inbox set duplicates = duplicates + 1 where message_id = :id")
                .param("id", messageId)
                .update();
    }

    /** @param duplicate {@code true} se o ERP já conhecia a mensagem e não criou nada */
    record Reception(long erpNumber, boolean duplicate) {
    }
}
