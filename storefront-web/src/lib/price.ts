// Preserve every cent without converting the decimal amount to a floating-point number.
export function formatBRL(value: string): string {
  if (!/^\d{1,17}\.\d{2}$/.test(value)) return "Preço indisponível";
  const [whole, fraction] = value.split(".");
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .formatToParts(BigInt(whole))
    .map(part => part.type === "fraction" ? fraction : part.value)
    .join("");
}
