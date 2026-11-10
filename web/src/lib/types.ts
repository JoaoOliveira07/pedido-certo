export type OrderStatus = "AGUARDANDO_PAGAMENTO" | "PAGO" | "INTEGRADO" | "FALHA_INTEGRACAO";

export type TimelineKind =
  | "PEDIDO_CRIADO"
  | "REQUISICAO_REPETIDA"
  | "PAGAMENTO_CONFIRMADO"
  | "WEBHOOK_REPETIDO"
  | "PAGAMENTO_RECUSADO"
  | "TENTATIVA_FALHOU"
  | "DUPLICATA_DESCARTADA"
  | "INTEGRADO"
  | "ENVIADO_PARA_DLQ"
  | "REPROCESSADO";

export interface OrderItem {
  sku: string;
  description: string;
  quantity: number;
  unitPriceCents: number;
}

export interface Order {
  id: string;
  customerName: string;
  status: OrderStatus;
  totalCents: number;
  pixTxid: string;
  erpNumber: number | null;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface TimelineEntry {
  at: string;
  kind: TimelineKind;
  detail: string;
}

export interface Stats {
  ordersTotal: number;
  ordersPaid: number;
  ordersIntegrated: number;
  erpOrders: number;
  duplicatesDiscarded: number;
  deliveryAttempts: number;
  deadLetters: number;
}

export interface Chaos {
  failureRate: number;
  ackLossRate: number;
  duplicateRate: number;
  latencyMs: number;
}

export interface NewOrder {
  customerName: string;
  items: OrderItem[];
}

/** Corpo de erro RFC 9457 devolvido pela API. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}
