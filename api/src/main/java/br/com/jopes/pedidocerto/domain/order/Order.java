package br.com.jopes.pedidocerto.domain.order;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Pedido. Toda mudança de status passa por aqui, então as regras de transição
 * ficam em um único lugar e são testáveis sem banco, sem Spring e sem rede.
 *
 * <p>As operações que podem ser repetidas por sistemas externos (webhook do PIX, confirmação
 * do ERP) são idempotentes: devolvem {@code false} quando o pedido já está no estado pedido,
 * em vez de lançar erro.
 */
public final class Order {

    public static final int MAX_ITEMS = 20;

    private final UUID id;
    private final String customerName;
    private final List<OrderItem> items;
    private final long totalCents;
    private final String pixTxid;
    private final Instant createdAt;
    private final int version;

    private OrderStatus status;
    private Long erpNumber;
    private Instant updatedAt;

    private Order(UUID id, String customerName, List<OrderItem> items, String pixTxid, OrderStatus status,
                  Long erpNumber, Instant createdAt, Instant updatedAt, int version) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerName = requireText(customerName);
        this.items = List.copyOf(items);
        this.pixTxid = Objects.requireNonNull(pixTxid, "pixTxid");
        this.status = Objects.requireNonNull(status, "status");
        this.erpNumber = erpNumber;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;

        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("pedido precisa de ao menos um item");
        }
        if (this.items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("pedido aceita no máximo " + MAX_ITEMS + " itens");
        }
        this.totalCents = this.items.stream().mapToLong(OrderItem::subtotalCents).reduce(0L, Math::addExact);
    }

    /** Cria um pedido novo, aguardando o pagamento PIX. */
    public static Order place(String customerName, List<OrderItem> items, Instant now) {
        UUID id = UUID.randomUUID();
        String txid = UUID.randomUUID().toString().replace("-", "");
        return new Order(id, customerName, items, txid, OrderStatus.AGUARDANDO_PAGAMENTO, null, now, now, 0);
    }

    /** Reconstrói um pedido já persistido. Usado apenas pelos adapters de persistência. */
    public static Order restore(UUID id, String customerName, List<OrderItem> items, String pixTxid,
                                OrderStatus status, Long erpNumber, Instant createdAt, Instant updatedAt,
                                int version) {
        return new Order(id, customerName, items, pixTxid, status, erpNumber, createdAt, updatedAt, version);
    }

    /**
     * Registra o pagamento. Webhooks de PIX podem chegar repetidos, então um pedido já pago
     * (ou já integrado) simplesmente ignora a repetição.
     *
     * @return {@code true} se o status mudou
     */
    public boolean markPaid(long amountCents, Instant now) {
        if (status != OrderStatus.AGUARDANDO_PAGAMENTO) {
            return false;
        }
        if (amountCents != totalCents) {
            throw new PaymentAmountMismatchException(totalCents, amountCents);
        }
        transitionTo(OrderStatus.PAGO, now);
        return true;
    }

    /**
     * Registra a confirmação do ERP. Uma confirmação repetida para o mesmo número é ignorada.
     *
     * @return {@code true} se o status mudou
     */
    public boolean markIntegrated(long erpNumber, Instant now) {
        if (status == OrderStatus.INTEGRADO) {
            if (!Objects.equals(this.erpNumber, erpNumber)) {
                throw new IllegalStateException(
                        "pedido " + id + " já integrado como " + this.erpNumber + ", recebeu " + erpNumber);
            }
            return false;
        }
        if (status != OrderStatus.PAGO) {
            throw new InvalidOrderTransitionException(status, "integrar");
        }
        this.erpNumber = erpNumber;
        transitionTo(OrderStatus.INTEGRADO, now);
        return true;
    }

    /** As tentativas de entrega ao ERP se esgotaram. */
    public void markIntegrationFailed(Instant now) {
        if (status != OrderStatus.PAGO) {
            throw new InvalidOrderTransitionException(status, "marcar falha de integração em");
        }
        transitionTo(OrderStatus.FALHA_INTEGRACAO, now);
    }

    /** Devolve um pedido com falha de integração para a fila. */
    public void retryIntegration(Instant now) {
        if (status != OrderStatus.FALHA_INTEGRACAO) {
            throw new InvalidOrderTransitionException(status, "reprocessar");
        }
        transitionTo(OrderStatus.PAGO, now);
    }

    private void transitionTo(OrderStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("nome do cliente é obrigatório");
        }
        return value.strip();
    }

    public UUID id() { return id; }
    public String customerName() { return customerName; }
    public List<OrderItem> items() { return items; }
    public long totalCents() { return totalCents; }
    public String pixTxid() { return pixTxid; }
    public OrderStatus status() { return status; }
    public Long erpNumber() { return erpNumber; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public int version() { return version; }
}
