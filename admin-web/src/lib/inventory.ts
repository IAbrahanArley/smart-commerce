export type Movement = {
  id: string; type: "ENTRY" | "ADJUSTMENT"; delta: number; balanceAfter: number;
  reason: string; createdAt: string; sequence: string;
};
export type InventorySnapshot = { quantity: number; movements: Movement[]; nextCursor: string | null };
export type MovementRequest = { requestId: string; type: "ENTRY" | "ADJUSTMENT"; delta: number; reason: string };

export function wholeQuantity(value: string): number | null {
  if (!/^[0-9]{1,10}$/.test(value.trim())) return null;
  const quantity = Number(value.trim());
  return quantity >= 1 && quantity <= 2147483647 ? quantity : null;
}
