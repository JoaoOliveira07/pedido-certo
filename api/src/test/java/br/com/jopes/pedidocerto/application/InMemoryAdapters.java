package br.com.jopes.pedidocerto.application;

import br.com.jopes.pedidocerto.application.port.out.ErpGateway;
import br.com.jopes.pedidocerto.application.port.out.IdempotencyStore;
import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.application.port.out.OrderRepository;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.application.port.out.TimelineRepository;
import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;
import br.com.jopes.pedidocerto.domain.order.Order;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import br.com.jopes.pedidocerto.domain.order.TimelineEntry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementações em memória das portas de saída. Permitem testar os casos de uso sem banco e
 * sem Spring, em milissegundos. O comportamento com Postgres de verdade é coberto pelos testes
 * de integração ({@code *IT}).
 */
final class InMemoryAdapters {

    private InMemoryAdapters() {
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class Orders implements OrderRepository {
        private final Map<UUID, Order> byId = new LinkedHashMap<>();

        @Override
        public void add(Order order) {
            byId.put(order.id(), copy(order, order.version()));
        }

        @Override
        public void update(Order order) {
            Order stored = byId.get(order.id());
            if (stored == null || stored.version() != order.version()) {
                throw new IllegalStateException("conflito de versão");
            }
            byId.put(order.id(), copy(order, order.version() + 1));
        }

        @Override
        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(byId.get(id)).map(o -> copy(o, o.version()));
        }

        @Override
        public Optional<Order> findByPixTxid(String txid) {
            return byId.values().stream().filter(o -> o.pixTxid().equals(txid)).findFirst()
                    .map(o -> copy(o, o.version()));
        }

        @Override
        public List<Order> findRecent(int limit) {
            return byId.values().stream()
                    .sorted(Comparator.comparing(Order::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        int size() {
            return byId.size();
        }

        private static Order copy(Order o, int version) {
            return Order.restore(o.id(), o.customerName(), o.items(), o.pixTxid(), o.status(), o.erpNumber(),
                    o.createdAt(), o.updatedAt(), version);
        }
    }

    static final class Keys implements IdempotencyStore {
        private final Map<String, StoredKey> keys = new HashMap<>();

        @Override
        public boolean tryReserve(String key, String requestHash) {
            return keys.putIfAbsent(key, new StoredKey(key, requestHash, null)) == null;
        }

        @Override
        public Optional<StoredKey> find(String key) {
            return Optional.ofNullable(keys.get(key));
        }

        @Override
        public void attachOrder(String key, UUID orderId) {
            StoredKey k = keys.get(key);
            keys.put(key, new StoredKey(key, k.requestHash(), orderId));
        }
    }

    static final class Outbox implements OutboxStore {

        static final class Message {
            final UUID id;
            final OrderPaidEvent event;
            String status = "PENDING";
            int attempts;
            Instant nextAttemptAt;
            Instant lockedUntil;
            String lastError;

            Message(UUID id, OrderPaidEvent event, Instant now) {
                this.id = id;
                this.event = event;
                this.nextAttemptAt = now;
            }
        }

        final Map<UUID, Message> messages = new LinkedHashMap<>();

        @Override
        public void enqueue(UUID messageId, OrderPaidEvent event, Instant now) {
            messages.put(messageId, new Message(messageId, event, now));
        }

        @Override
        public List<OutboxEntry> claimDue(int limit, Instant now, Duration lease) {
            List<OutboxEntry> claimed = new ArrayList<>();
            for (Message m : messages.values()) {
                if (claimed.size() == limit) {
                    break;
                }
                boolean due = (m.status.equals("PENDING") && !m.nextAttemptAt.isAfter(now))
                        || (m.status.equals("IN_FLIGHT") && m.lockedUntil.isBefore(now));
                if (due) {
                    m.status = "IN_FLIGHT";
                    m.lockedUntil = now.plus(lease);
                    m.attempts++;
                    claimed.add(new OutboxEntry(m.id, m.event, m.attempts));
                }
            }
            return claimed;
        }

        @Override
        public void markDelivered(UUID messageId, Instant now) {
            messages.get(messageId).status = "DELIVERED";
        }

        @Override
        public void scheduleRetry(UUID messageId, Instant nextAttemptAt, String error) {
            Message m = messages.get(messageId);
            m.status = "PENDING";
            m.nextAttemptAt = nextAttemptAt;
            m.lastError = error;
        }

        @Override
        public void markDead(UUID messageId, String error) {
            Message m = messages.get(messageId);
            m.status = "DEAD";
            m.lastError = error;
        }

        @Override
        public int requeueDead(UUID orderId, Instant now) {
            int count = 0;
            for (Message m : messages.values()) {
                if (m.event.orderId().equals(orderId) && m.status.equals("DEAD")) {
                    m.status = "PENDING";
                    m.attempts = 0;
                    m.nextAttemptAt = now;
                    count++;
                }
            }
            return count;
        }
    }

    static final class Timeline implements TimelineRepository {
        final List<TimelineEntry> entries = new ArrayList<>();

        @Override
        public void append(TimelineEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<TimelineEntry> findByOrder(UUID orderId) {
            return entries.stream().filter(e -> e.orderId().equals(orderId)).toList();
        }
    }

    static final class Events implements OrderEventsPublisher {
        final List<OrderStatus> published = new ArrayList<>();

        @Override
        public void orderChanged(UUID orderId, OrderStatus status) {
            published.add(status);
        }
    }

    /**
     * ERP idempotente com um roteiro de comportamentos de rede, consumido a cada entrega:
     * {@code OK}, {@code TIMEOUT} (não chega) ou {@code ACK_LOST} (chega, resposta se perde).
     * Sem roteiro, tudo dá certo.
     */
    static final class ScriptedErp implements ErpGateway {

        enum Network { OK, TIMEOUT, ACK_LOST }

        private final Deque<Network> script = new ArrayDeque<>();
        private final Map<UUID, Long> inbox = new HashMap<>();
        private final Map<UUID, Long> ordersInErp = new HashMap<>();
        private long nextNumber = 1001;
        private Network fallback = Network.OK;
        int duplicatesDiscarded;

        ScriptedErp then(Network... steps) {
            script.addAll(List.of(steps));
            return this;
        }

        ScriptedErp always(Network network) {
            this.fallback = network;
            return this;
        }

        @Override
        public ErpReceipt deliver(UUID messageId, OrderPaidEvent event) {
            Network network = script.isEmpty() ? fallback : script.poll();
            if (network == Network.TIMEOUT) {
                throw new ErpUnavailableException("timeout");
            }
            int duplicates = 0;
            Long number = inbox.get(messageId);
            if (number != null) {
                duplicates = 1;
                duplicatesDiscarded++;
            } else {
                number = ordersInErp.computeIfAbsent(event.orderId(), ignored -> nextNumber++);
                inbox.put(messageId, number);
            }
            if (network == Network.ACK_LOST) {
                throw new ErpUnavailableException("ack perdido");
            }
            return new ErpReceipt(number, duplicates);
        }

        int ordersCreated() {
            return ordersInErp.size();
        }
    }
}
