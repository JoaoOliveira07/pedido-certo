package br.com.jopes.pedidocerto.integration;

import br.com.jopes.pedidocerto.application.port.in.ChaosControlUseCase;
import br.com.jopes.pedidocerto.application.port.in.DeliverPendingUseCase;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo contra um Postgres de verdade (Testcontainers): HTTP, transações, outbox com
 * SKIP LOCKED, inbox do ERP e as restrições do banco. O relay agendado fica desligado e cada
 * rodada é disparada pelo teste, para o resultado não depender de tempo.
 */
@SpringBootTest(properties = {
        "app.relay.enabled=false",
        "app.demo.retention-check-ms=86400000"
})
@AutoConfigureMockMvc
@Testcontainers
class OrderFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String BODY = """
            {"customerName": "Mercado Teste",
             "items": [{"sku": "LIN-5KG", "description": "Linguiça toscana 5 kg", "quantity": 2, "unitPriceCents": 8990}]}
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    DeliverPendingUseCase relay;
    @Autowired
    ChaosControlUseCase chaos;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void calmNetwork() {
        chaos.update(ChaosSettings.CALM);
    }

    @Test
    void placeReplayPayAndIntegrate() throws Exception {
        String key = newKey();
        String id = place(key);

        mvc.perform(post("/api/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(post("/api/demo/orders/{id}/pay", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes[0]").value("CONFIRMED"));

        relay.deliverDue();

        mvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTEGRADO"))
                .andExpect(jsonPath("$.erpNumber").isNumber());
        mvc.perform(get("/api/orders/{id}/timeline", id))
                .andExpect(jsonPath("$[0].kind").value("PEDIDO_CRIADO"));
    }

    @Test
    void duplicatedDeliveriesAndLostAcksNeverDuplicateTheErpOrder() throws Exception {
        String id = place(newKey());
        mvc.perform(post("/api/demo/orders/{id}/pay", id)).andExpect(status().isOk());

        // toda entrega chega duplicada e a resposta se perde
        chaos.update(new ChaosSettings(0, 1, 1, 0));
        relay.deliverDue();
        mvc.perform(get("/api/orders/{id}", id)).andExpect(jsonPath("$.status").value("PAGO"));

        chaos.update(ChaosSettings.CALM);
        skipBackoff();
        relay.deliverDue();

        mvc.perform(get("/api/orders/{id}", id)).andExpect(jsonPath("$.status").value("INTEGRADO"));
        assertEquals(1, erpOrdersFor(id), "um pedido no ERP, apesar de 3 entregas");
        long duplicates = jdbc.sql("""
                        select i.duplicates from erp.inbox i join erp.orders o on o.erp_number = i.erp_number
                         where o.order_id = :id
                        """)
                .param("id", UUID.fromString(id))
                .query(Long.class)
                .single();
        assertEquals(2, duplicates);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateASingleOrder() throws Exception {
        String key = newKey();
        int clients = 8;
        List<Callable<String>> calls = java.util.Collections.nCopies(clients, () -> {
            MvcResult result = mvc.perform(post("/api/orders").header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn();
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        });

        Set<String> ids = new HashSet<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            for (Future<String> future : pool.invokeAll(calls)) {
                ids.add(future.get());
            }
        }

        assertEquals(1, ids.size(), "todas as respostas apontam para o mesmo pedido");
        long orders = jdbc.sql("select count(*) from idempotency_keys where key = :key and order_id is not null")
                .param("key", key)
                .query(Long.class)
                .single();
        assertEquals(1, orders);
    }

    @Test
    void sameKeyWithAnotherBodyIsRejected() throws Exception {
        String key = newKey();
        place(key);
        mvc.perform(post("/api/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("Mercado Teste", "Outro Mercado")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("idempotency-key-reutilizada")));
    }

    @Test
    void missingOrMalformedKeyIsABadRequest() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Idempotency-Key", "curta")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidOrderIsRejectedWithProblemDetails() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\": \"\", \"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")));
    }

    @Test
    void webhookWithAnInvalidSignatureIsRejected() throws Exception {
        mvc.perform(post("/api/webhooks/pix")
                        .header("X-Pix-Signature", "sha256=00")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txid\": \"abc\", \"amountCents\": 100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exhaustedDeliveriesGoToTheDeadLetterQueueAndCanBeRetried() throws Exception {
        String id = place(newKey());
        mvc.perform(post("/api/demo/orders/{id}/pay", id)).andExpect(status().isOk());

        chaos.update(new ChaosSettings(1, 0, 0, 0));
        for (int attempt = 0; attempt < 6; attempt++) {
            relay.deliverDue();
            skipBackoff();
        }
        mvc.perform(get("/api/orders/{id}", id)).andExpect(jsonPath("$.status").value("FALHA_INTEGRACAO"));

        chaos.update(ChaosSettings.CALM);
        mvc.perform(post("/api/orders/{id}/retry-integration", id)).andExpect(status().isAccepted());
        relay.deliverDue();

        mvc.perform(get("/api/orders/{id}", id)).andExpect(jsonPath("$.status").value("INTEGRADO"));
        assertEquals(1, erpOrdersFor(id));
        assertTrue(jdbc.sql("select count(*) from outbox where status = 'DEAD' and aggregate_id = :id")
                .param("id", UUID.fromString(id)).query(Long.class).single() == 0);
    }

    private String place(String key) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private long erpOrdersFor(String orderId) {
        return jdbc.sql("select count(*) from erp.orders where order_id = :id")
                .param("id", UUID.fromString(orderId))
                .query(Long.class)
                .single();
    }

    /** Antecipa as mensagens em backoff, para o teste não precisar esperar o relógio. */
    private void skipBackoff() {
        jdbc.sql("update outbox set next_attempt_at = now() where status = 'PENDING'").update();
    }

    private static String newKey() {
        return "it-" + UUID.randomUUID();
    }
}
