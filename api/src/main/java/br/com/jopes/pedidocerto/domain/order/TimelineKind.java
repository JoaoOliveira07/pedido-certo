package br.com.jopes.pedidocerto.domain.order;

/** Tudo o que acontece com um pedido e vale mostrar para quem acompanha o fluxo. */
public enum TimelineKind {
    PEDIDO_CRIADO,
    REQUISICAO_REPETIDA,
    PAGAMENTO_CONFIRMADO,
    WEBHOOK_REPETIDO,
    PAGAMENTO_RECUSADO,
    TENTATIVA_FALHOU,
    DUPLICATA_DESCARTADA,
    INTEGRADO,
    ENVIADO_PARA_DLQ,
    REPROCESSADO
}
