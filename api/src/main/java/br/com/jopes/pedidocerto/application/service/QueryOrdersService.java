package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.OrderNotFoundException;
import br.com.jopes.pedidocerto.application.port.in.QueryOrdersUseCase;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.StatsQuery;
import br.com.jopes.pedidocerto.application.port.out.StatsQuery.IntegrationStats;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class QueryOrdersService implements QueryOrdersUseCase {

    public static final int MAX_PAGE = 100;

    private final OrderRepository orders;
    private final TimelineRepository timeline;
    private final StatsQuery stats;

    public QueryOrdersService(OrderRepository orders, TimelineRepository timeline, StatsQuery stats) {
        this.orders = orders;
        this.timeline = timeline;
        this.stats = stats;
    }

    @Override
    public Order get(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    public List<Order> recent(int limit) {
        return orders.findRecent(Math.clamp(limit, 1, MAX_PAGE));
    }

    @Override
    public List<TimelineEntry> timeline(UUID orderId) {
        get(orderId);
        return timeline.findByOrder(orderId);
    }

    @Override
    public IntegrationStats stats() {
        return stats.load();
    }
}
