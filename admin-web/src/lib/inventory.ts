export type Movement = {
  id: string; type: "ENTRY" | "ADJUSTMENT"; delta: number; balanceAfter: number;
  reason: string; createdAt: string; sequence: string;
};
/** quantity = saldo físico; reserved = reservas ativas não vencidas; available = max(0, físico − reservado). */
export type InventorySnapshot = { quantity: number; reserved: number; available: number; movements: Movement[]; nextCursor: string | null };
export type MovementRequest = { requestId: string; type: "ENTRY" | "ADJUSTMENT"; delta: number; reason: string };

export function wholeQuantity(value: string): number | null {
  if (!/^[0-9]{1,10}$/.test(value.trim())) return null;
  const quantity = Number(value.trim());
  return quantity >= 1 && quantity <= 2147483647 ? quantity : null;
}

/** D7 (ADR-0012): ajuste negativo que deixaria o físico abaixo do reservado. */
export function reservedStockMessage(reserved: unknown): string {
  const units = typeof reserved === "number" && Number.isSafeInteger(reserved) && reserved > 0 ? reserved : null;
  return `${units === null ? "Há unidades reservadas" : `Há ${new Intl.NumberFormat("pt-BR").format(units)} ${units === 1 ? "unidade reservada" : "unidades reservadas"}`} por pedidos pendentes. O saldo físico não pode ficar abaixo do reservado; retire no máximo o disponível ou aguarde a liberação das reservas.`;
}
