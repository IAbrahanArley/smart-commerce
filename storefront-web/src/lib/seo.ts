// Sprint 8 (SC-047, ADR-0018 V11): what search engines read. Pure helpers, free of React and Next
// imports for the smoke test: the public origin, canonical paths, robots per environment and the
// JSON-LD of Product, BreadcrumbList and Organization. Everything comes from data the backend
// already published; nothing private reaches these documents.

/** Cache tag of every public read of a store (SC-047): the backend invalidates it by slug. */
export function storeTag(slug: string): string {
  return `store:${slug}`;
}

/** The public https origin of storefront-web, or null when not configured (local development). */
export function publicOrigin(value: string | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.pathname !== "/" || url.search || url.hash) return null;
    return url.origin;
  } catch {
    return null;
  }
}

/** Store pages are indexed only in production: the hosted test environment never is. */
export function indexable(deployment: string | undefined): boolean {
  return deployment?.trim().toLowerCase() !== "hosted-test";
}

export function storePath(store: string): string {
  return `/lojas/${encodeURIComponent(store)}`;
}

/** Plain text of sanitized description markup, cut at a word for meta descriptions. */
export function plainText(html: string, max = 160): string {
  // Block tags separate words; inline ones (strong, em) do not.
  const text = html.replace(/<\/?(?:p|li|ul|ol|br)\b[^>]*>/gi, " ").replace(/<[^>]*>/g, "").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&#x27;|&#39;/g, "'").replace(/\s+/g, " ").trim();
  if (text.length <= max) return text;
  const cut = text.slice(0, max - 1);
  const space = cut.lastIndexOf(" ");
  return `${(space > max / 2 ? cut.slice(0, space) : cut).trimEnd()}…`;
}

/** JSON for a <script type="application/ld+json">: "<" escaped so no tag can close the script. */
export function jsonLd(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

type Crumb = { name: string; path: string };

export function breadcrumbLd(origin: string, crumbs: Crumb[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: crumbs.map((crumb, index) => ({ "@type": "ListItem", position: index + 1, name: crumb.name, item: `${origin}${crumb.path}` })),
  };
}

export type ProductLdInput = {
  name: string; description: string; path: string; images: string[]; brand: string | null; currency: "BRL";
  variants: { name: string | null; price: string; available: boolean }[];
};

/** One Offer per variant, with its own price and availability (ADR-0018 V11). */
export function productLd(origin: string, product: ProductLdInput) {
  const url = `${origin}${product.path}`;
  const offer = (variant: ProductLdInput["variants"][number]) => ({
    "@type": "Offer", url, price: variant.price, priceCurrency: product.currency,
    availability: variant.available ? "https://schema.org/InStock" : "https://schema.org/OutOfStock",
    itemCondition: "https://schema.org/NewCondition",
    ...(variant.name ? { name: variant.name } : {}),
  });
  return {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.name,
    url,
    ...(product.description ? { description: product.description } : {}),
    ...(product.images.length ? { image: product.images.map(path => `${origin}${path}`) } : {}),
    ...(product.brand ? { brand: { "@type": "Brand", name: product.brand } } : {}),
    offers: product.variants.length === 1 ? offer(product.variants[0]) : product.variants.map(offer),
  };
}

export type OrganizationLdInput = {
  name: string; path: string; logo: string | null; phone: string | null; email: string | null; legalName: string | null;
  sameAs: string[];
};

export function organizationLd(origin: string, store: OrganizationLdInput) {
  return {
    "@context": "https://schema.org",
    "@type": "Organization",
    name: store.name,
    url: `${origin}${store.path}`,
    ...(store.legalName ? { legalName: store.legalName } : {}),
    ...(store.logo ? { logo: `${origin}${store.logo}` } : {}),
    ...(store.phone || store.email ? { contactPoint: { "@type": "ContactPoint", contactType: "customer service", ...(store.phone ? { telephone: `+${store.phone}` } : {}), ...(store.email ? { email: store.email } : {}) } } : {}),
    ...(store.sameAs.length ? { sameAs: store.sameAs } : {}),
  };
}

export type SitemapInput = { products: { slug: string; updatedAt: string }[]; categories: string[]; pages: string[] };

const XML: Record<string, string> = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" };

/** The sitemap.xml of one store: home, categories, pages and products (with their last change). */
export function sitemapXml(origin: string, store: string, input: SitemapInput): string {
  const base = `${origin}${storePath(store)}`;
  const entry = (loc: string, lastmod?: string) => `<url><loc>${loc.replace(/[&<>"']/g, char => XML[char])}</loc>${lastmod ? `<lastmod>${lastmod}</lastmod>` : ""}</url>`;
  return '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'
    + entry(base)
    + input.categories.map(slug => entry(`${base}/c/${encodeURIComponent(slug)}`)).join("")
    + input.pages.map(slug => entry(`${base}/paginas/${encodeURIComponent(slug)}`)).join("")
    + input.products.map(product => entry(`${base}/p/${encodeURIComponent(product.slug)}`, product.updatedAt)).join("")
    + "</urlset>\n";
}

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** The backend sitemap answer, keeping only well-formed addresses. */
export function parseSitemap(raw: unknown): SitemapInput | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  if (!Array.isArray(value.products) || !Array.isArray(value.categories) || !Array.isArray(value.pages)) return null;
  const slugs = (list: unknown[]) => list.filter((slug): slug is string => typeof slug === "string" && slug.length <= 80 && SLUG.test(slug));
  return {
    products: value.products.flatMap(entry => {
      if (!entry || typeof entry !== "object") return [];
      const product = entry as Record<string, unknown>;
      return typeof product.slug === "string" && product.slug.length <= 80 && SLUG.test(product.slug)
        && typeof product.updatedAt === "string" && !Number.isNaN(Date.parse(product.updatedAt))
        ? [{ slug: product.slug, updatedAt: new Date(product.updatedAt).toISOString() }] : [];
    }).slice(0, 5000),
    categories: slugs(value.categories).slice(0, 200),
    pages: slugs(value.pages).slice(0, 10),
  };
}
