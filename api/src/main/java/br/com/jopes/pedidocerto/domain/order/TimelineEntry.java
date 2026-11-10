package br.com.jopes.pedidocerto.domain.order;

import java.time.Instant;
import java.util.UUID;

public record TimelineEntry(UUID orderId, Instant at, TimelineKind kind, String detail) {
}
