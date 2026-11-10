"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, REPO_URL } from "@/lib/config";
import type { Chaos } from "@/lib/types";
import { useLiveDashboard } from "@/lib/useLiveDashboard";
import { ChaosPanel } from "./ChaosPanel";
import { NewOrderForm } from "./NewOrderForm";
import { OrderList } from "./OrderList";
import { Pipeline } from "./Pipeline";

const connectionLabel = {
  connecting: "Conectando à API…",
  live: "Ao vivo",
  offline: "Sem conexão com a API",
} as const;

export function Dashboard() {
  const live = useLiveDashboard(api);
  const [chaos, setChaos] = useState<Chaos | null>(null);

  useEffect(() => {
    api.chaos().then(setChaos).catch(() => undefined);
  }, [live.revision]);

  return (
    <>
      <header className="masthead">
        <Link className="wordmark" href="/">
          Pedido Certo
        </Link>
        <nav aria-label="Links">
          <a href={REPO_URL}>Código no GitHub</a>
          <a href="https://www.jopes.com.br">Portfólio</a>
        </nav>
      </header>

      <main>
        <section className="intro">
          <h1>Cada pedido chega ao ERP uma vez só.</h1>
          <p>
            Mesmo quando a rede perde, repete ou atrasa mensagens. Crie um pedido, pague o PIX, piore a rede e
            acompanhe o caminho até o ERP.
          </p>
          <p className="connection" data-state={live.connection} role="status">
            {connectionLabel[live.connection]}
          </p>
        </section>

        {live.error && (
          <p className="banner" role="alert">
            {live.error}
          </p>
        )}

        <Pipeline stats={live.stats} chaos={chaos} pulses={live.pulses} onPulseDone={live.dropPulse} />

        <div className="workspace">
          <aside className="controls">
            <NewOrderForm api={api} onPlaced={live.refresh} />
            <ChaosPanel api={api} chaos={chaos} onChange={setChaos} />
          </aside>
          <OrderList api={api} orders={live.orders} revision={live.revision} loaded={live.stats !== null} />
        </div>

        <section className="explainer" aria-labelledby="how-title">
          <h2 id="how-title">Como a garantia funciona</h2>
          <div className="explainer-grid">
            <article>
              <h3>Idempotency-Key na criação</h3>
              <p>
                Toda criação de pedido leva uma chave única. Se a mesma chave chega de novo, a API devolve o pedido
                original em vez de criar outro. Duas requisições simultâneas esperam uma pela outra no banco.
              </p>
            </article>
            <article>
              <h3>Outbox na mesma transação</h3>
              <p>
                Quando o PIX é confirmado, o status do pedido e a mensagem para o ERP são gravados juntos. Não existe
                pedido pago sem mensagem, nem mensagem de pedido que não foi pago.
              </p>
            </article>
            <article>
              <h3>ERP que descarta repetições</h3>
              <p>
                A entrega é repetida até dar certo, então o ERP pode receber a mesma mensagem mais de uma vez. Ele
                guarda o id de cada mensagem e, na repetição, devolve o número que já tinha criado.
              </p>
            </article>
          </div>
          <p>
            As decisões, com os trade-offs, estão nos <a href={`${REPO_URL}/tree/main/docs/adr`}>ADRs do repositório</a>.
          </p>
        </section>
      </main>

      <footer className="footer">
        Feito por João Paulo Oliveira com Spring Boot, Postgres e Next.js. Os pedidos são fictícios e somem depois de
        24 horas.
      </footer>
    </>
  );
}
