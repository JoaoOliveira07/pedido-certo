import type { OrderStatus, TimelineKind } from "./types";

const brlFormat = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const timeFormat = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  timeZone: "America/Sao_Paulo",
});

export function brl(cents: number): string {
  return brlFormat.format(cents / 100);
}

export function clock(iso: string): string {
  return timeFormat.format(new Date(iso));
}

export function percent(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

/** Os 8 primeiros caracteres do UUID bastam para identificar um pedido na tela. */
export function shortId(id: string): string {
  return id.slice(0, 8);
}

export const statusLabel: Record<OrderStatus, string> = {
  AGUARDANDO_PAGAMENTO: "Aguardando PIX",
  PAGO: "Pago, indo para o ERP",
  INTEGRADO: "No ERP",
  FALHA_INTEGRACAO: "Falhou, na DLQ",
};

export type Tone = "neutral" | "progress" | "ok" | "warn" | "fail";

export const statusTone: Record<OrderStatus, Tone> = {
  AGUARDANDO_PAGAMENTO: "neutral",
  PAGO: "progress",
  INTEGRADO: "ok",
  FALHA_INTEGRACAO: "fail",
};

export const timelineTone: Record<TimelineKind, Tone> = {
  PEDIDO_CRIADO: "neutral",
  REQUISICAO_REPETIDA: "warn",
  PAGAMENTO_CONFIRMADO: "progress",
  WEBHOOK_REPETIDO: "warn",
  PAGAMENTO_RECUSADO: "fail",
  TENTATIVA_FALHOU: "fail",
  DUPLICATA_DESCARTADA: "warn",
  INTEGRADO: "ok",
  ENVIADO_PARA_DLQ: "fail",
  REPROCESSADO: "progress",
};
