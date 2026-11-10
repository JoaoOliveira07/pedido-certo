import type { Chaos, NewOrder, Order, Problem, Stats, TimelineEntry } from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | null,
  ) {
    super(problem?.detail ?? problem?.title ?? `A API respondeu ${status}`);
    this.name = "ApiError";
  }
}

export interface PlacedOrder {
  order: Order;
  /** A API reconheceu a Idempotency-Key e devolveu o pedido original. */
  replayed: boolean;
}

type Fetch = typeof fetch;

export function newIdempotencyKey(): string {
  return `web-${crypto.randomUUID()}`;
}

export function createApi(baseUrl: string, fetchImpl: Fetch = (...args) => fetch(...args)) {
  const root = baseUrl.replace(/\/+$/, "");

  async function request(path: string, init?: RequestInit): Promise<Response> {
    let response: Response;
    try {
      response = await fetchImpl(`${root}${path}`, init);
    } catch {
      throw new ApiError(0, { detail: "Não foi possível falar com a API. Ela pode estar iniciando; tente de novo em alguns segundos." });
    }
    if (!response.ok) {
      throw new ApiError(response.status, await readProblem(response));
    }
    return response;
  }

  async function json<T>(path: string, init?: RequestInit): Promise<T> {
    return (await request(path, init)).json() as Promise<T>;
  }

  return {
    streamUrl: `${root}/api/stream`,

    listOrders: (limit = 30) => json<Order[]>(`/api/orders?limit=${limit}`),
    timeline: (id: string) => json<TimelineEntry[]>(`/api/orders/${encodeURIComponent(id)}/timeline`),
    stats: () => json<Stats>("/api/stats"),
    chaos: () => json<Chaos>("/api/demo/chaos"),

    async placeOrder(input: NewOrder, idempotencyKey: string): Promise<PlacedOrder> {
      const response = await request("/api/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(input),
      });
      return {
        order: (await response.json()) as Order,
        replayed: response.headers.get("Idempotent-Replayed") === "true",
      };
    },

    pay: (id: string) =>
      json<{ webhooksSent: number; outcomes: string[] }>(`/api/demo/orders/${encodeURIComponent(id)}/pay`, {
        method: "POST",
      }),

    async retryIntegration(id: string): Promise<void> {
      await request(`/api/orders/${encodeURIComponent(id)}/retry-integration`, { method: "POST" });
    },

    updateChaos: (chaos: Chaos) =>
      json<Chaos>("/api/demo/chaos", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(chaos),
      }),
  };
}

export type Api = ReturnType<typeof createApi>;

async function readProblem(response: Response): Promise<Problem | null> {
  try {
    return (await response.json()) as Problem;
  } catch {
    return null;
  }
}
