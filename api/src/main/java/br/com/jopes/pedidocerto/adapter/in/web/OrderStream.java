package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.application.port.out.OrderEventsPublisher;
import br.com.jopes.pedidocerto.domain.order.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal de Server-Sent Events do painel. É o adapter web fazendo o papel de saída: a aplicação
 * avisa que um pedido mudou e este componente repassa para os navegadores conectados.
 *
 * <p>Vale para uma instância só. Com várias, o aviso precisaria passar por um broker
 * (Postgres LISTEN/NOTIFY, Redis) para chegar a todos — ver ADR 0005.
 */
@Component
class OrderStream implements OrderEventsPublisher {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_SUBSCRIBERS = 300;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    SseEmitter subscribe() {
        if (emitters.size() >= MAX_SUBSCRIBERS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Muitos painéis conectados agora");
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, SseEmitter.event().name("ready").data("ok"));
        return emitter;
    }

    /** Publica só depois do commit, para o painel nunca buscar um estado que ainda pode voltar atrás. */
    @Override
    public void orderChanged(UUID orderId, OrderStatus status) {
        OrderChanged payload = new OrderChanged(orderId, status);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(payload);
                }
            });
        } else {
            broadcast(payload);
        }
    }

    /** Comentário periódico para proxies (Caddy, Cloudflare) não fecharem a conexão ociosa. */
    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    private void broadcast(OrderChanged payload) {
        emitters.forEach(emitter ->
                send(emitter, SseEmitter.event().name("order").data(payload, MediaType.APPLICATION_JSON)));
    }

    private void send(SseEmitter emitter, SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    public record OrderChanged(UUID orderId, OrderStatus status) {
    }
}
