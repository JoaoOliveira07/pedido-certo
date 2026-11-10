package br.com.jopes.pedidocerto.adapter.out.persistence;

import br.com.jopes.pedidocerto.application.port.out.StatsQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStatsQuery implements StatsQuery {

    private final JdbcClient jdbc;

    JdbcStatsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public IntegrationStats load() {
        return jdbc.sql("""
                        select (select count(*) from orders)                                          as orders_total,
                               (select count(*) from orders where status <> 'AGUARDANDO_PAGAMENTO')   as orders_paid,
                               (select count(*) from orders where status = 'INTEGRADO')               as orders_integrated,
                               (select count(*) from erp.orders)                                      as erp_orders,
                               (select coalesce(sum(duplicates), 0) from erp.inbox)                   as duplicates,
                               (select coalesce(sum(attempts), 0) from outbox)                        as attempts,
                               (select count(*) from outbox where status = 'DEAD')                    as dead
                        """)
                .query((rs, rowNum) -> new IntegrationStats(
                        rs.getLong("orders_total"),
                        rs.getLong("orders_paid"),
                        rs.getLong("orders_integrated"),
                        rs.getLong("erp_orders"),
                        rs.getLong("duplicates"),
                        rs.getLong("attempts"),
                        rs.getLong("dead")))
                .single();
    }
}
