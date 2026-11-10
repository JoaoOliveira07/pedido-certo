package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.order.TimelineEntry;

import java.util.List;
import java.util.UUID;

public interface TimelineRepository {

    void append(TimelineEntry entry);

    List<TimelineEntry> findByOrder(UUID orderId);
}
