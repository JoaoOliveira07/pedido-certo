package br.com.jopes.pedidocerto.domain.order;

/**
 * Ciclo de vida de um pedido.
 *
 * <pre>
 * AGUARDANDO_PAGAMENTO ──pago──▶ PAGO ──ERP confirmou──▶ INTEGRADO
 *                                  │  ▲
 *                 tentativas       │  │ reprocessar
 *                 esgotadas        ▼  │
 *                           FALHA_INTEGRACAO
 * </pre>
 */
public enum OrderStatus {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    INTEGRADO,
    FALHA_INTEGRACAO
}
