package br.com.jopes.pedidocerto.adapter.out.persistence;

import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.instant;
import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.ts;

@Repository
class JdbcTimelineRepository implements TimelineRepository {

    private final JdbcClient jdbc;

    JdbcTimelineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(TimelineEntry entry) {
        jdbc.sql("insert into order_timeline (order_id, at, kind, detail) values (:order, :at, :kind, :detail)")
                .param("order", entry.orderId())
                .param("at", ts(entry.at()))
                .param("kind", entry.kind().name())
                .param("detail", entry.detail())
                .update();
    }

    @Override
    public List<TimelineEntry> findByOrder(UUID orderId) {
        return jdbc.sql("select order_id, at, kind, detail from order_timeline where order_id = :order order by id")
                .param("order", orderId)
                .query((rs, rowNum) -> new TimelineEntry(
                        rs.getObject("order_id", UUID.class),
                        instant(rs, "at"),
                        TimelineKind.valueOf(rs.getString("kind")),
                        rs.getString("detail")))
                .list();
    }
}
