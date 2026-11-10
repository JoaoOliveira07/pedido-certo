"use client";

import { useState } from "react";
import { newIdempotencyKey, type Api } from "@/lib/api";
import { catalog, randomCustomer } from "@/lib/catalog";
import { brl, shortId } from "@/lib/format";

interface Props {
  api: Api;
  onPlaced: () => void;
}

type Feedback = { tone: "ok" | "warn" | "fail"; text: string } | null;

const initialQuantities = Object.fromEntries(catalog.map((item, index) => [item.sku, index < 2 ? 2 - index : 0]));

export function NewOrderForm({ api, onPlaced }: Props) {
  const [customer, setCustomer] = useState(randomCustomer);
  const [quantities, setQuantities] = useState<Record<string, number>>(initialQuantities);
  const [sendTwice, setSendTwice] = useState(true);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const items = catalog
    .map((item) => ({ ...item, quantity: quantities[item.sku] ?? 0 }))
    .filter((item) => item.quantity > 0);
  const total = items.reduce((sum, item) => sum + item.quantity * item.unitPriceCents, 0);

  function setQuantity(sku: string, value: number) {
    setQuantities((current) => ({ ...current, [sku]: Math.min(99, Math.max(0, value)) }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (items.length === 0) {
      setFeedback({ tone: "fail", text: "Escolha ao menos um produto." });
      return;
    }
    setBusy(true);
    setFeedback(null);
    const key = newIdempotencyKey();
    const input = { customerName: customer, items };
    try {
      if (sendTwice) {
        // o clique duplo de verdade: duas requisições ao mesmo tempo, mesma chave
        const [a, b] = await Promise.all([api.placeOrder(input, key), api.placeOrder(input, key)]);
        const same = a.order.id === b.order.id;
        setFeedback({
          tone: same ? "ok" : "fail",
          text: same
            ? `Duas requisições, um pedido só: as duas voltaram com o pedido ${shortId(a.order.id)}.`
            : "As duas requisições criaram pedidos diferentes. Isso é um bug.",
        });
      } else {
        const placed = await api.placeOrder(input, key);
        setFeedback({ tone: "ok", text: `Pedido ${shortId(placed.order.id)} criado. Agora pague o PIX na lista.` });
      }
      setCustomer(randomCustomer());
      onPlaced();
    } catch (e) {
      setFeedback({ tone: "fail", text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel" onSubmit={submit} aria-labelledby="new-order-title">
      <h2 id="new-order-title">Novo pedido</h2>

      <label className="field">
        <span>Cliente</span>
        <input value={customer} onChange={(e) => setCustomer(e.target.value)} maxLength={120} required />
      </label>

      <fieldset className="products">
        <legend>Produtos</legend>
        {catalog.map((item) => {
          const quantity = quantities[item.sku] ?? 0;
          return (
            <div key={item.sku} className="product" data-active={quantity > 0 ? "" : undefined}>
              <span className="product-name">{item.description}</span>
              <span className="product-price">{brl(item.unitPriceCents)}</span>
              <span className="stepper">
                <button
                  type="button"
                  onClick={() => setQuantity(item.sku, quantity - 1)}
                  aria-label={`Menos ${item.description}`}
                  disabled={quantity === 0}
                >
                  −
                </button>
                <output aria-label={`Quantidade de ${item.description}`}>{quantity}</output>
                <button
                  type="button"
                  onClick={() => setQuantity(item.sku, quantity + 1)}
                  aria-label={`Mais ${item.description}`}
                >
                  +
                </button>
              </span>
            </div>
          );
        })}
      </fieldset>

      <label className="check">
        <input type="checkbox" checked={sendTwice} onChange={(e) => setSendTwice(e.target.checked)} />
        <span>
          Enviar duas vezes ao mesmo tempo
          <small>Simula o clique duplo. A Idempotency-Key garante um pedido só.</small>
        </span>
      </label>

      <div className="form-footer">
        <span className="total">
          Total <strong>{brl(total)}</strong>
        </span>
        <button type="submit" className="primary" disabled={busy}>
          {busy ? "Criando…" : "Criar pedido"}
        </button>
      </div>

      <p className="feedback" role="status" data-tone={feedback?.tone}>
        {feedback?.text}
      </p>
    </form>
  );
}
