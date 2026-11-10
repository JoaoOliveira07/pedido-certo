package br.com.jopes.pedidocerto.application.port.out;

/** Lado de leitura: números agregados para o painel, calculados direto no banco. */
public interface StatsQuery {

    IntegrationStats load();

    /**
     * @param ordersPaid          pedidos que já foram pagos (inclui os integrados)
     * @param ordersIntegrated    pedidos confirmados pelo ERP
     * @param erpOrders           pedidos que existem no ERP
     * @param duplicatesDiscarded entregas repetidas que o ERP descartou
     * @param deliveryAttempts    tentativas de entrega feitas pelo outbox
     * @param deadLetters         mensagens que esgotaram as tentativas
     */
    record IntegrationStats(long ordersTotal, long ordersPaid, long ordersIntegrated, long erpOrders,
                            long duplicatesDiscarded, long deliveryAttempts, long deadLetters) {
    }
}
