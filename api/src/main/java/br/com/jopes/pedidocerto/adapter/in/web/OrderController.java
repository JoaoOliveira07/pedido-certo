package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase;
import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import br.com.jopes.pedidocerto.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import br.com.jopes.pedidocerto.application.port.in.QueryOrdersUseCase;
import br.com.jopes.pedidocerto.application.port.in.RetryIntegrationUseCase;
import br.com.jopes.pedidocerto.application.port.out.StatsQuery.IntegrationStats;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_\\-:.]{8,100}");

    private final PlaceOrderUseCase placeOrder;
    private final QueryOrdersUseCase queries;
    private final RetryIntegrationUseCase retryIntegration;
    private final OrderStream stream;

    OrderController(PlaceOrderUseCase placeOrder, QueryOrdersUseCase queries,
                    RetryIntegrationUseCase retryIntegration, OrderStream stream) {
        this.placeOrder = placeOrder;
        this.queries = queries;
        this.retryIntegration = retryIntegration;
        this.stream = stream;
    }

    /**
     * Cria um pedido. O header {@code Idempotency-Key} é obrigatório: repetir a requisição com a
     * mesma chave (porque a resposta se perdeu, por exemplo) devolve o mesmo pedido.
     */
    @PostMapping("/orders")
    ResponseEntity<OrderResponse> place(@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                        @Valid @RequestBody CreateOrderRequest request) {
        if (!VALID_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        PlaceOrderResult result = placeOrder.place(
                new PlaceOrderCommand(idempotencyKey, request.customerName(), request.toDomain()));
        OrderResponse body = OrderResponse.from(result.order());

        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).header(REPLAYED, "false").body(body);
    }

    @GetMapping("/orders")
    List<OrderResponse> recent(@RequestParam(defaultValue = "30") int limit) {
        return queries.recent(limit).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/orders/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(queries.get(id));
    }

    @GetMapping("/orders/{id}/timeline")
    List<TimelineResponse> timeline(@PathVariable UUID id) {
        return queries.timeline(id).stream().map(TimelineResponse::from).toList();
    }

    @PostMapping("/orders/{id}/retry-integration")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void retry(@PathVariable UUID id) {
        retryIntegration.retry(id);
    }

    @GetMapping("/stats")
    IntegrationStats stats() {
        return queries.stats();
    }

    /** Server-Sent Events: o painel recebe um aviso a cada mudança e busca o que precisar. */
    @GetMapping(path = "/stream", produces = "text/event-stream")
    SseEmitter subscribe() {
        return stream.subscribe();
    }

    static class InvalidIdempotencyKeyException extends RuntimeException {
        InvalidIdempotencyKeyException() {
            super("Idempotency-Key deve ter de 8 a 100 caracteres: letras, números, '-', '_', ':' ou '.'");
        }
    }
}
