import type { OrderItem } from "./types";

/** Catálogo fictício de um distribuidor de frios para supermercados. */
export const catalog: OrderItem[] = [
  { sku: "LIN-TOS-5", description: "Linguiça toscana, caixa 5 kg", quantity: 1, unitPriceCents: 8990 },
  { sku: "SAL-HOT-3", description: "Salsicha hot dog, pacote 3 kg", quantity: 1, unitPriceCents: 4550 },
  { sku: "PRE-FAT-1", description: "Presunto fatiado, 1 kg", quantity: 1, unitPriceCents: 3290 },
  { sku: "BAC-MAN-2", description: "Bacon em manta, 2 kg", quantity: 1, unitPriceCents: 9480 },
  { sku: "MOR-DEF-4", description: "Mortadela defumada, peça 4 kg", quantity: 1, unitPriceCents: 6120 },
];

export const customers = [
  "Mercado São Cristóvão",
  "Supermercado Vale do Itajaí",
  "Empório da Esquina",
  "Atacadão Litoral",
  "Mercearia Dona Lurdes",
  "Rede Bom Preço",
];

export function randomCustomer(): string {
  return customers[Math.floor(Math.random() * customers.length)] ?? customers[0]!;
}

export const chaosPresets = {
  calm: { label: "Rede boa", chaos: { failureRate: 0, ackLossRate: 0, duplicateRate: 0, latencyMs: 0 } },
  flaky: { label: "Rede ruim", chaos: { failureRate: 0.2, ackLossRate: 0.15, duplicateRate: 0.25, latencyMs: 150 } },
  storm: { label: "Tempestade", chaos: { failureRate: 0.5, ackLossRate: 0.4, duplicateRate: 0.6, latencyMs: 400 } },
} as const;
