package br.com.jopes.pedidocerto.application.port.in;

import java.util.UUID;

public interface RetryIntegrationUseCase {

    /** Devolve para a fila um pedido cuja entrega ao ERP esgotou as tentativas. */
    void retry(UUID orderId);
}
