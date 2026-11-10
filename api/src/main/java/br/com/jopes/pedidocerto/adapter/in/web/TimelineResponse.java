package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.domain.order.TimelineEntry;
import br.com.jopes.pedidocerto.domain.order.TimelineKind;

import java.time.Instant;

public record TimelineResponse(Instant at, TimelineKind kind, String detail) {

    static TimelineResponse from(TimelineEntry entry) {
        return new TimelineResponse(entry.at(), entry.kind(), entry.detail());
    }
}
