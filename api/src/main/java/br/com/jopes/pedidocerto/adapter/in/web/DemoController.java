package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.application.port.in.ChaosControlUseCase;
import br.com.jopes.pedidocerto.application.port.in.ConfirmPaymentUseCase.PaymentOutcome;
import br.com.jopes.pedidocerto.application.port.in.QueryOrdersUseCase;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import br.com.jopes.pedidocerto.domain.order.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Endpoints que só existem para a demonstração: o "PSP" que paga o PIX e o controle do caos.
 * Desligados com {@code app.demo.enabled=false}.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
class DemoController {

    private final QueryOrdersUseCase queries;
    private final PixWebhookHandler webhook;
    private final ChaosControlUseCase chaos;
    private final ObjectMapper json;

    DemoController(QueryOrdersUseCase queries, PixWebhookHandler webhook, ChaosControlUseCase chaos,
                   ObjectMapper json) {
        this.queries = queries;
        this.webhook = webhook;
        this.chaos = chaos;
        this.json = json;
    }

    /**
     * Simula o PSP pagando o PIX: monta o webhook, assina e entrega pelo mesmo caminho de um
     * webhook real. Com caos de duplicação, o "PSP" reenvia o mesmo webhook, como acontece de verdade.
     */
    @PostMapping("/orders/{id}/pay")
    PaymentSimulation pay(@PathVariable UUID id) {
        Order order = queries.get(id);
        String body = write(new PixWebhookHandler.PixWebhookPayload(order.pixTxid(), order.totalCents()));
        String signature = webhook.sign(body);

        List<PaymentOutcome> outcomes = new ArrayList<>();
        outcomes.add(webhook.handle(body, signature));
        double duplicateRate = chaos.current().duplicateRate();
        if (duplicateRate > 0 && ThreadLocalRandom.current().nextDouble() < duplicateRate) {
            outcomes.add(webhook.handle(body, signature));
        }
        return new PaymentSimulation(outcomes.size(), outcomes);
    }

    @GetMapping("/chaos")
    ChaosSettings currentChaos() {
        return chaos.current();
    }

    @PutMapping("/chaos")
    ChaosSettings updateChaos(@Valid @RequestBody ChaosRequest request) {
        return chaos.update(request.toDomain());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record PaymentSimulation(int webhooksSent, List<PaymentOutcome> outcomes) {
    }

    public record ChaosRequest(
            @DecimalMin("0") @DecimalMax("1") double failureRate,
            @DecimalMin("0") @DecimalMax("1") double ackLossRate,
            @DecimalMin("0") @DecimalMax("1") double duplicateRate,
            @Min(0) @Max(ChaosSettings.MAX_LATENCY_MS) int latencyMs) {

        ChaosSettings toDomain() {
            return new ChaosSettings(failureRate, ackLossRate, duplicateRate, latencyMs);
        }
    }
}
