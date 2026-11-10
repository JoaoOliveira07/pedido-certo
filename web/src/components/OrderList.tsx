"use client";

import { useEffect, useState } from "react";
import type { Api } from "@/lib/api";
import { brl, clock, shortId, statusLabel, statusTone, timelineTone } from "@/lib/format";
import type { Order, TimelineEntry } from "@/lib/types";

interface Props {
  api: Api;
  orders: Order[];
  revision: number;
  loaded: boolean;
}

export function OrderList({ api, orders, revision, loaded }: Props) {
  const [openId, setOpenId] = useState<string | null>(null);

  if (loaded && orders.length === 0) {
    return (
      <section className="orders" aria-labelledby="orders-title">
        <h2 id="orders-title">Pedidos</h2>
        <p className="empty">Nenhum pedido nas últimas 24 horas. Crie um ao lado e acompanhe a viagem até o ERP.</p>
      </section>
    );
  }

  return (
    <section className="orders" aria-labelledby="orders-title">
      <h2 id="orders-title">Pedidos</h2>
      <ul className="order-list">
        {orders.map((order) => (
          <OrderRow
            key={order.id}
            api={api}
            order={order}
            open={openId === order.id}
            revision={revision}
            onToggle={() => setOpenId((current) => (current === order.id ? null : order.id))}
          />
        ))}
      </ul>
    </section>
  );
}

interface RowProps {
  api: Api;
  order: Order;
  open: boolean;
  revision: number;
  onToggle: () => void;
}

function OrderRow({ api, order, open, revision, onToggle }: RowProps) {
  const [timeline, setTimeline] = useState<TimelineEntry[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panelId = `order-${order.id}`;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    api
      .timeline(order.id)
      .then((entries) => !cancelled && setTimeline(entries))
      .catch((e: unknown) => !cancelled && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      cancelled = true;
    };
  }, [api, open, order.id, revision]);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="order" data-open={open ? "" : undefined}>
      <div className="order-head">
        <button type="button" className="order-toggle" aria-expanded={open} aria-controls={panelId} onClick={onToggle}>
          <span className="order-id">{shortId(order.id)}</span>
          <span className="order-customer">{order.customerName}</span>
          <span className="order-total">{brl(order.totalCents)}</span>
          <span className="status" data-tone={statusTone[order.status]}>
            {statusLabel[order.status]}
            {order.erpNumber !== null && <> nº {order.erpNumber}</>}
          </span>
        </button>
        <span className="order-action">
        {order.status === "AGUARDANDO_PAGAMENTO" && (
          <button type="button" className="secondary" disabled={busy} onClick={() => run(() => api.pay(order.id))}>
            Pagar PIX
          </button>
        )}
        {order.status === "FALHA_INTEGRACAO" && (
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => run(() => api.retryIntegration(order.id))}
          >
            Reprocessar
          </button>
        )}
        </span>
      </div>

      {error && (
        <p className="feedback" role="alert" data-tone="fail">
          {error}
        </p>
      )}

      {open && (
        <div id={panelId} className="order-detail">
          <ul className="items">
            {order.items.map((item) => (
              <li key={item.sku}>
                {item.quantity} × {item.description}
              </li>
            ))}
          </ul>
          {timeline === null ? (
            <p className="muted">Carregando histórico…</p>
          ) : (
            <ol className="timeline">
              {timeline.map((entry, index) => (
                <li key={index} data-tone={timelineTone[entry.kind]}>
                  <time dateTime={entry.at}>{clock(entry.at)}</time>
                  <span>{entry.detail}</span>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </li>
  );
}
