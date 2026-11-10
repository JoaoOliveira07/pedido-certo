"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { Api } from "./api";
import type { Order, OrderStatus, Stats } from "./types";

export type Connection = "connecting" | "live" | "offline";

export interface Pulse {
  id: number;
  status: OrderStatus;
}

const REFRESH_DEBOUNCE_MS = 150;
const FALLBACK_POLL_MS = 15_000;

/**
 * Mantém pedidos e números atualizados. O servidor só avisa "o pedido X mudou" por SSE; o painel
 * agrupa os avisos de um instante e busca o estado de novo. Assim a fonte da verdade continua
 * sendo a API, e um aviso perdido não deixa a tela errada para sempre (há também um polling lento).
 */
export function useLiveDashboard(api: Api) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [connection, setConnection] = useState<Connection>("connecting");
  const [error, setError] = useState<string | null>(null);
  const [pulses, setPulses] = useState<Pulse[]>([]);
  const [revision, setRevision] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pulseId = useRef(0);

  const refresh = useCallback(async () => {
    try {
      const [nextOrders, nextStats] = await Promise.all([api.listOrders(), api.stats()]);
      setOrders(nextOrders);
      setStats(nextStats);
      setRevision((r) => r + 1);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [api]);

  const scheduleRefresh = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void refresh(), REFRESH_DEBOUNCE_MS);
  }, [refresh]);

  useEffect(() => {
    // primeira carga assim que o painel monta
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
    const poll = setInterval(() => void refresh(), FALLBACK_POLL_MS);

    const source = new EventSource(api.streamUrl);
    source.addEventListener("ready", () => setConnection("live"));
    source.addEventListener("order", (event) => {
      const { status } = JSON.parse((event as MessageEvent<string>).data) as { status: OrderStatus };
      const id = ++pulseId.current;
      setPulses((current) => [...current.slice(-11), { id, status }]);
      scheduleRefresh();
    });
    // o EventSource reconecta sozinho; só mostramos o estado
    source.onerror = () => setConnection(source.readyState === EventSource.CLOSED ? "offline" : "connecting");

    return () => {
      clearInterval(poll);
      source.close();
      if (timer.current) clearTimeout(timer.current);
    };
  }, [api, refresh, scheduleRefresh]);

  const dropPulse = useCallback((id: number) => {
    setPulses((current) => current.filter((p) => p.id !== id));
  }, []);

  return { orders, stats, connection, error, pulses, dropPulse, refresh, revision };
}
