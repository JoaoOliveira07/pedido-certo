package br.com.jopes.pedidocerto.adapter.out.persistence;

import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderItem;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.instant;
import static br.com.jopes.pedidocerto.adapter.out.persistence.Jdbc.ts;

@Repository
class JdbcOrderRepository implements OrderRepository {

    private static final String SELECT_ORDER = """
            select id, customer_name, status, pix_txid, erp_number, created_at, updated_at, version
              from orders
            """;

    private final JdbcClient jdbc;

    JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Order order) {
        jdbc.sql("""
                        insert into orders (id, customer_name, status, total_cents, pix_txid, erp_number,
                                            created_at, updated_at, version)
                        values (:id, :customer, :status, :total, :txid, :erp, :created, :updated, :version)
                        """)
                .param("id", order.id())
                .param("customer", order.customerName())
                .param("status", order.status().name())
                .param("total", order.totalCents())
                .param("txid", order.pixTxid())
                .param("erp", order.erpNumber())
                .param("created", ts(order.createdAt()))
                .param("updated", ts(order.updatedAt()))
                .param("version", order.version())
                .update();

        int line = 1;
        for (OrderItem item : order.items()) {
            jdbc.sql("""
                            insert into order_items (order_id, line, sku, description, quantity, unit_price_cents)
                            values (:order, :line, :sku, :description, :quantity, :price)
                            """)
                    .param("order", order.id())
                    .param("line", line++)
                    .param("sku", item.sku())
                    .param("description", item.description())
                    .param("quantity", item.quantity())
                    .param("price", item.unitPriceCents())
                    .update();
        }
    }

    @Override
    public void update(Order order) {
        int updated = jdbc.sql("""
                        update orders
                           set status = :status, erp_number = :erp, updated_at = :updated, version = version + 1
                         where id = :id and version = :version
                        """)
                .param("status", order.status().name())
                .param("erp", order.erpNumber())
                .param("updated", ts(order.updatedAt()))
                .param("id", order.id())
                .param("version", order.version())
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Pedido " + order.id() + " foi alterado por outro processo; leia de novo e tente outra vez");
        }
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql(SELECT_ORDER + " where id = :id")
                .param("id", id)
                .query(OrderRow::from)
                .optional()
                .map(row -> row.toOrder(itemsOf(List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public Optional<Order> findByPixTxid(String txid) {
        return jdbc.sql(SELECT_ORDER + " where pix_txid = :txid")
                .param("txid", txid)
                .query(OrderRow::from)
                .optional()
                .map(row -> row.toOrder(itemsOf(List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public List<Order> findRecent(int limit) {
        List<OrderRow> rows = jdbc.sql(SELECT_ORDER + " order by created_at desc limit :limit")
                .param("limit", limit)
                .query(OrderRow::from)
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItem>> items = itemsOf(rows.stream().map(OrderRow::id).toList());
        return rows.stream().map(row -> row.toOrder(items.getOrDefault(row.id(), List.of()))).toList();
    }

    /** Uma consulta para os itens de todos os pedidos da página, em vez de uma por pedido (N+1). */
    private Map<UUID, List<OrderItem>> itemsOf(List<UUID> orderIds) {
        Map<UUID, List<OrderItem>> byOrder = new LinkedHashMap<>();
        jdbc.sql("""
                        select order_id, sku, description, quantity, unit_price_cents
                          from order_items
                         where order_id in (:ids)
                         order by order_id, line
                        """)
                .param("ids", orderIds)
                .query(rs -> {
                    UUID orderId = rs.getObject("order_id", UUID.class);
                    byOrder.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(new OrderItem(
                            rs.getString("sku"),
                            rs.getString("description"),
                            rs.getInt("quantity"),
                            rs.getLong("unit_price_cents")));
                });
        return byOrder;
    }

    private record OrderRow(UUID id, String customerName, OrderStatus status, String pixTxid, Long erpNumber,
                            java.time.Instant createdAt, java.time.Instant updatedAt, int version) {

        static OrderRow from(ResultSet rs, int rowNum) throws SQLException {
            long erp = rs.getLong("erp_number");
            return new OrderRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("customer_name"),
                    OrderStatus.valueOf(rs.getString("status")),
                    rs.getString("pix_txid"),
                    rs.wasNull() ? null : erp,
                    instant(rs, "created_at"),
                    instant(rs, "updated_at"),
                    rs.getInt("version"));
        }

        Order toOrder(List<OrderItem> items) {
            return Order.restore(id, customerName, items, pixTxid, status, erpNumber, createdAt, updatedAt, version);
        }
    }
}
