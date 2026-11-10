package br.com.jopes.pedidocerto.application.port.in;

public interface DeliverPendingUseCase {

    /**
     * Envia ao ERP as mensagens do outbox que estão prontas.
     *
     * @return quantas mensagens foram processadas nesta rodada (com sucesso ou não)
     */
    int deliverDue();
}
