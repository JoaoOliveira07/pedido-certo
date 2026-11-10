package br.com.jopes.pedidocerto.application.port.out;

import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;

import java.util.UUID;

/** Porta para o ERP. Quem implementa decide o transporte (HTTP, fila, simulação). */
public interface ErpGateway {

    /**
     * Entrega um pedido pago ao ERP.
     *
     * <p>O ERP é um consumidor idempotente: a mesma {@code messageId} entregue duas vezes gera
     * um único pedido no ERP, e a segunda entrega devolve o número já existente.
     *
     * @throws ErpUnavailableException se não deu para saber se o ERP recebeu (timeout, ack perdido)
     */
    ErpReceipt deliver(UUID messageId, OrderPaidEvent event);

    /**
     * @param erpNumber           número do pedido no ERP
     * @param duplicatesDiscarded quantas entregas repetidas desta mensagem o ERP descartou nesta chamada
     */
    record ErpReceipt(long erpNumber, int duplicatesDiscarded) {
    }

    class ErpUnavailableException extends RuntimeException {
        public ErpUnavailableException(String message) {
            super(message);
        }
    }
}
