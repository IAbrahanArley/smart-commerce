// Exact sum of two decimal amounts in cents (BigInt): totals shown before the backend confirms them.
export function addMoney(left: string, right: string): string | null {
  if (!/^\d{1,20}\.\d{2}$/.test(left) || !/^\d{1,20}\.\d{2}$/.test(right)) return null;
  const cents = BigInt(left.replace(".", "")) + BigInt(right.replace(".", ""));
  const digits = cents.toString().padStart(3, "0");
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

// Preserve every cent without converting the decimal amount to a floating-point number.
// Up to 20 integer digits: cart totals (20 lines × 10 units) exceed the 17-digit unit price.
export function formatBRL(value: string): string {
  if (!/^\d{1,20}\.\d{2}$/.test(value)) return "Preço indisponível";
  const [whole, fraction] = value.split(".");
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .formatToParts(BigInt(whole))
    .map(part => part.type === "fraction" ? fraction : part.value)
    .join("");
}
