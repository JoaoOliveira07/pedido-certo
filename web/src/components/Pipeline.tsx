"use client";

import type { Chaos, OrderStatus, Stats } from "@/lib/types";
import type { Pulse } from "@/lib/useLiveDashboard";

interface Props {
  stats: Stats | null;
  chaos: Chaos | null;
  pulses: Pulse[];
  onPulseDone: (id: number) => void;
}

/** Posição (0 a 4) de cada estação na esteira, usada para animar o pulso até ela. */
const stationOf: Record<OrderStatus, number> = {
  AGUARDANDO_PAGAMENTO: 0,
  PAGO: 2,
  INTEGRADO: 4,
  FALHA_INTEGRACAO: 3,
};

export function Pipeline({ stats, chaos, pulses, onPulseDone }: Props) {
  const s = stats ?? {
    ordersTotal: 0,
    ordersPaid: 0,
    ordersIntegrated: 0,
    erpOrders: 0,
    duplicatesDiscarded: 0,
    deliveryAttempts: 0,
    deadLetters: 0,
  };
  const queued = Math.max(0, s.ordersPaid - s.ordersIntegrated - s.deadLetters);
  const severity = networkSeverity(chaos);

  const stations = [
    { name: "Loja", value: s.ordersTotal, caption: "pedidos criados" },
    { name: "PIX", value: s.ordersPaid, caption: "pagos" },
    { name: "Outbox", value: queued, caption: "esperando entrega" },
    { name: "Rede", value: s.deliveryAttempts, caption: "tentativas de entrega", network: true },
    { name: "ERP", value: s.erpOrders, caption: "pedidos criados lá" },
  ];

  return (
    <section className="pipeline" aria-label="Caminho do pedido até o ERP">
      <div className="track" data-severity={severity}>
        <div className="rail" aria-hidden="true">
          <span className="rail-network" />
        </div>
        {pulses.map((pulse) => {
          const to = stationOf[pulse.status];
          const from = Math.max(0, to - 1);
          return (
            <span
              key={pulse.id}
              className="pulse"
              data-status={pulse.status}
              aria-hidden="true"
              style={{ "--from": from, "--to": to } as React.CSSProperties}
              onAnimationEnd={() => onPulseDone(pulse.id)}
            />
          );
        })}
        <ol className="stations">
          {stations.map((station) => (
            <li key={station.name} className="station" data-network={station.network ? "" : undefined}>
              <span className="station-dot" aria-hidden="true" />
              <span className="station-name">{station.name}</span>
              <span className="station-value">{station.value}</span>
              <span className="station-caption">{station.caption}</span>
            </li>
          ))}
        </ol>
      </div>

      <dl className="ledger">
        <div>
          <dt>Duplicatas que o ERP descartou</dt>
          <dd>{s.duplicatesDiscarded}</dd>
        </div>
        <div>
          <dt>Mensagens na fila de mortas (DLQ)</dt>
          <dd data-tone={s.deadLetters > 0 ? "fail" : undefined}>{s.deadLetters}</dd>
        </div>
        <div>
          <dt>Pedidos em dobro no ERP</dt>
          <dd data-tone="ok">0</dd>
        </div>
      </dl>
    </section>
  );
}

function networkSeverity(chaos: Chaos | null): "calm" | "flaky" | "storm" {
  if (!chaos) return "calm";
  const worst = Math.max(chaos.failureRate, chaos.ackLossRate, chaos.duplicateRate);
  if (worst === 0) return "calm";
  return worst >= 0.4 ? "storm" : "flaky";
}
