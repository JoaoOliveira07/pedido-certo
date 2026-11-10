package br.com.jopes.pedidocerto.application.service;

import br.com.jopes.pedidocerto.application.port.in.DeliverPendingUseCase;
import br.com.jopes.pedidocerto.application.port.out.ErpGateway;
import br.com.jopes.pedidocerto.application.port.out.ErpGateway.ErpReceipt;
import br.com.jopes.pedidocerto.application.port.out.ErpGateway.ErpUnavailableException;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore;
import br.com.jopes.pedidocerto.application.port.out.OutboxStore.OutboxEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Lê o outbox e entrega as mensagens ao ERP.
 *
 * <p>Não abre transação em volta da chamada ao ERP de propósito: segurar uma conexão do banco
 * esperando a rede é o jeito mais rápido de esgotar o pool. A reserva da mensagem e o registro do
 * resultado são transações curtas e separadas.
 *
 * <p>A entrega é "pelo menos uma vez": se o resultado não puder ser registrado, a reserva expira e
 * a mensagem é enviada de novo. Quem garante o "exatamente uma vez" no efeito final é o ERP, que
 * descarta repetições pelo id da mensagem.
 */
@Service
public class OutboxRelay implements DeliverPendingUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore outbox;
    private final ErpGateway erp;
    private final DeliveryOutcomes outcomes;
    private final RelaySettings settings;
    private final Clock clock;

    public OutboxRelay(OutboxStore outbox, ErpGateway erp, DeliveryOutcomes outcomes, RelaySettings settings,
                       Clock clock) {
        this.outbox = outbox;
        this.erp = erp;
        this.outcomes = outcomes;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public int deliverDue() {
        List<OutboxEntry> batch = outbox.claimDue(settings.batchSize(), clock.instant(), settings.lease());
        for (OutboxEntry entry : batch) {
            try {
                deliver(entry);
            } catch (RuntimeException e) {
                // a reserva vai expirar e a mensagem volta sozinha; só não deixamos derrubar o lote
                log.warn("Falha ao registrar o resultado da mensagem {}", entry.messageId(), e);
            }
        }
        return batch.size();
    }

    private void deliver(OutboxEntry entry) {
        ErpReceipt receipt;
        try {
            receipt = erp.deliver(entry.messageId(), entry.event());
        } catch (ErpUnavailableException e) {
            outcomes.failed(entry, e.getMessage());
            return;
        }
        outcomes.delivered(entry, receipt);
    }
}
