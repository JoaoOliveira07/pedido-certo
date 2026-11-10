package br.com.jopes.pedidocerto.application.port.in;

import br.com.jopes.pedidocerto.application.port.out.StatsQuery.IntegrationStats;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;

import java.util.List;
import java.util.UUID;

public interface QueryOrdersUseCase {

    Order get(UUID orderId);

    List<Order> recent(int limit);

    List<TimelineEntry> timeline(UUID orderId);

    IntegrationStats stats();
}
