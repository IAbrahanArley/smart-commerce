export type TenantStore = {
  id: string;
  companyName: string;
  store: { id: string; name: string; slug: string; status: "DRAFT" | "PUBLISHED" };
  role: "OWNER" | "STAFF";
};

export function storefrontUrl(slug: string): string | null {
  try {
    const origin = new URL(process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN ?? "http://localhost:3001");
    if (!["http:", "https:"].includes(origin.protocol) || origin.username || origin.password) return null;
    return new URL(`/lojas/${encodeURIComponent(slug)}`, origin.origin).toString();
  } catch { return null; }
}
