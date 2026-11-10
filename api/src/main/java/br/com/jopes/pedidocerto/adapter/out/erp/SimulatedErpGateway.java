package br.com.jopes.pedidocerto.adapter.out.erp;

import br.com.jopes.pedidocerto.adapter.out.erp.SimulatedErp.Reception;
import br.com.jopes.pedidocerto.application.port.out.ErpGateway;
import br.com.jopes.pedidocerto.application.port.out.NetworkChaos;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import br.com.jopes.pedidocerto.domain.integration.OrderPaidEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A "rede" entre a loja e o ERP, com as falhas que acontecem de verdade em integração:
 * a mensagem não chega, chega duas vezes, ou chega e a resposta se perde.
 *
 * <p>O caso do ack perdido é o mais traiçoeiro: o ERP já criou o pedido, mas quem enviou não sabe
 * e vai mandar de novo. Sem idempotência no ERP, isso vira pedido duplicado.
 */
@Component
class SimulatedErpGateway implements ErpGateway {

    private final SimulatedErp erp;
    private final NetworkChaos network;

    SimulatedErpGateway(SimulatedErp erp, NetworkChaos network) {
        this.erp = erp;
        this.network = network;
    }

    @Override
    public ErpReceipt deliver(UUID messageId, OrderPaidEvent event) {
        ChaosSettings chaos = network.current();
        pause(chaos.latencyMs());

        if (happens(chaos.failureRate())) {
            throw new ErpUnavailableException("timeout: a mensagem não chegou ao ERP");
        }

        Reception reception = erp.receive(messageId, event);
        int duplicates = reception.duplicate() ? 1 : 0;

        if (happens(chaos.duplicateRate())) {
            Reception again = erp.receive(messageId, event);
            duplicates += again.duplicate() ? 1 : 0;
        }

        if (happens(chaos.ackLossRate())) {
            throw new ErpUnavailableException("ack perdido: o ERP processou, mas a resposta não voltou");
        }
        return new ErpReceipt(reception.erpNumber(), duplicates);
    }

    private static boolean happens(double probability) {
        return probability > 0 && ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static void pause(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErpUnavailableException("envio interrompido");
        }
    }
}
