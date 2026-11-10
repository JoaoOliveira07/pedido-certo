package br.com.jopes.pedidocerto.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.ts;

/**
 * A demo é pública: qualquer pessoa cria pedidos. Para o banco não crescer sem limite,
 * pedidos mais velhos que a retenção são apagados, nos dois lados (loja e ERP simulado).
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
class DemoDataRetention {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRetention.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration retention;

    DemoDataRetention(JdbcClient jdbc, Clock clock,
                      @Value("${app.demo.retention:24h}") Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.demo.retention-check-ms:600000}")
    @Transactional
    public void purge() {
        var cutoff = ts(clock.instant().minus(retention));
        jdbc.sql("""
                        delete from erp.orders
                         where order_id in (select id from orders where created_at < :cutoff)
                        """)
                .param("cutoff", cutoff)
                .update();
        int removed = jdbc.sql("delete from orders where created_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
        if (removed > 0) {
            log.info("Limpeza da demo: {} pedido(s) com mais de {} removidos", removed, retention);
        }
    }
}
