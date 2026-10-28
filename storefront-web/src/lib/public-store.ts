import "server-only";
import { notFound } from "next/navigation";
import { type Frame, EMPTY_FRAME, parseFrame } from "@/lib/frame";
import { type SitemapInput, parseSitemap, storeTag } from "@/lib/seo";
import { type Card, type CatalogPage, type Link, parseCard, parseCatalog, parseInstallments } from "@/lib/catalog-page";

export type PublicProduct = {
  id: string;
  name: string;
  price: string;
  currency: "BRL";
  available: boolean;
  descriptionHtml: string;
  kind: "SIMPLE" | "VARIABLE";
  variants: PublicVariant[];
};
export type PublicVariant = { id: string; price: string; currency: "BRL"; available: boolean; options: { name: string; value: string }[] };
export type PublicImage = { id: string; alt: string; width: number; height: number };

// MVP block 1b: public contacts and logo the store chose to show; null contacts are not shown.
export type StoreProfile = { contactEmail: string | null; whatsapp: string | null; instagram: string | null; logo: boolean };
export type PublicStore = { name: string; slug: string; profile: StoreProfile };
export type PublicCategory = { id: string; name: string };
export type PublicCatalog = PublicStore & { categories: PublicCategory[]; category: string | null; products: PublicProduct[]; nextCursor: string | null };
/** SC-045: what the product page shows besides the product itself (SC-042 API). */
export type ProductExtras = {
  slug: string | null; brand: Link | null; categoryPath: Link[]; compareAtPrice: string | null; discountPercent: number | null;
  /** "Compare at" price of each variant id, only when above its price. */
  variantCompareAt: Record<string, string>;
  related: Card[]; maxInstallments: number | null;
};
export type PublicProductDetail = { store: PublicStore; product: PublicProduct; images: PublicImage[]; extras: ProductExtras };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function unavailable(): never {
  // Never include upstream URLs, response bodies or personal data in the render error.
  throw new Error("Não foi possível consultar a vitrine. Tente novamente.");
}

export function slugPath(slug: string): string {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug)) notFound();
  return `/api/public/stores/${encodeURIComponent(slug)}`;
}

export function apiOrigin(): string {
  try {
    const value = new URL(process.env.STOREFRONT_API_ORIGIN ?? "http://127.0.0.1:8080");
    if (!['http:', 'https:'].includes(value.protocol) || value.username || value.password
      || value.pathname !== "/" || value.search || value.hash) unavailable();
    return value.origin;
  } catch {
    return unavailable();
  }
}

/**
 * SC-047 (ADR-0018 V9): public reads are cached for 60 s under the tag of their store, so the key
 * always contains the store (the URL) and the backend can drop one store's reads after a change.
 * Pages that must stay fresh (cart, checkout, order) force no-store on their own segment.
 */
function cached(path: string): RequestInit {
  const store = /^\/api\/public\/stores\/([a-z0-9-]+)/.exec(path)?.[1];
  return store ? { next: { revalidate: 60, tags: [storeTag(store)] } } : { cache: "no-store" };
}

async function publicGet(path: string): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${path}`, {
      ...cached(path),
      credentials: "omit",
      redirect: "error",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(5000),
    });
  } catch {
    return unavailable();
  }
  // 400: a filter in the URL the API refused (SC-045); like a missing page for the buyer.
  if (response.status === 404 || response.status === 400) notFound();
  if (!response.ok) unavailable();
  try {
    return await response.json();
  } catch {
    return unavailable();
  }
}

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) unavailable();
  return value as Record<string, unknown>;
}

function name(value: unknown): string {
  if (typeof value !== "string" || !value.trim() || [...value].length > 120) unavailable();
  return value;
}

function store(value: unknown, expectedSlug: string): PublicStore {
  const input = object(value);
  if (input.slug !== expectedSlug) unavailable();
  return { name: name(input.name), slug: expectedSlug, profile: storeProfile(input.profile) };
}

/** Same rules as the backend; anything unexpected is dropped instead of shown. */
export function storeProfile(value: unknown): StoreProfile {
  const input = value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
  const email = typeof input.contactEmail === "string" && input.contactEmail.length <= 254
    && /^[^@\s<>"]{1,64}@[^@\s.<>"]+(\.[^@\s.<>"]+)+$/.test(input.contactEmail) ? input.contactEmail : null;
  const whatsapp = typeof input.whatsapp === "string" && /^55[1-9]\d{9,10}$/.test(input.whatsapp) ? input.whatsapp : null;
  const instagram = typeof input.instagram === "string" && /^[A-Za-z0-9._]{1,30}$/.test(input.instagram) ? input.instagram : null;
  return { contactEmail: email, whatsapp, instagram, logo: input.logo === true };
}

/** "5511988887777" → "(11) 98888-7777". */
export function whatsappText(digits: string): string {
  const match = /^55(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  return match ? `(${match[1]}) ${match[2]}-${match[3]}` : digits;
}

function product(value: unknown): PublicProduct {
  const input = object(value);
  if (typeof input.id !== "string" || !UUID.test(input.id)
    || typeof input.price !== "string" || !/^\d{1,17}\.\d{2}$/.test(input.price)
    || /^0+\.00$/.test(input.price) || input.currency !== "BRL"
    || typeof input.available !== "boolean" || !["SIMPLE", "VARIABLE"].includes(String(input.kind))
    || typeof input.descriptionHtml !== "string" || [...input.descriptionHtml].length > 20000
    || new TextEncoder().encode(input.descriptionHtml).length > 80000 || !Array.isArray(input.variants) || input.variants.length > 50
    || (input.kind === "SIMPLE" && input.variants.length > 1)) unavailable();
  // A second, strict boundary accepts only the backend's seven canonical, attribute-free tags.
  if (input.descriptionHtml.replace(/<\/?(?:p|strong|em|ul|ol|li)>|<br\s*\/?>/g, "").includes("<")) unavailable();
  const variants = input.variants.map(value => {
    const variant = object(value);
    if (typeof variant.id !== "string" || !UUID.test(variant.id) || typeof variant.price !== "string" || !/^\d{1,17}\.\d{2}$/.test(variant.price)
      || /^0+\.00$/.test(variant.price) || variant.currency !== "BRL" || typeof variant.available !== "boolean" || !Array.isArray(variant.options)
      || (input.kind === "SIMPLE" ? variant.options.length !== 0 : variant.options.length < 1 || variant.options.length > 3)) unavailable();
    const options = variant.options.map(value => {
      const option = object(value); const label = name(option.name); const content = name(option.value);
      if ([...label].length > 40 || [...content].length > 40) unavailable();
      return { name: label, value: content };
    });
    return { id: variant.id, price: variant.price, currency: "BRL" as const, available: variant.available, options };
  });
  // Project only public fields even if a future backend accidentally adds private properties.
  return { id: input.id, name: name(input.name), price: input.price, currency: "BRL", available: input.available, descriptionHtml: input.descriptionHtml, kind: input.kind as "SIMPLE" | "VARIABLE", variants };
}

/**
 * SC-044/SC-043: the frame of a store page (menu, company details, pages), from the first page of
 * the public catalog. A failure only hides it; the page around it keeps its own error handling.
 */
export async function loadFrame(slug: string): Promise<Frame> {
  try {
    return parseFrame(await publicGet(`${slugPath(slug)}/catalog`));
  } catch {
    return EMPTY_FRAME;
  }
}

/** SC-047: what the store sitemap lists (published content only), or null for an unknown store. */
export async function loadSitemap(slug: string): Promise<SitemapInput | null> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug)) return null;
  try {
    const response = await fetch(`${apiOrigin()}${slugPath(slug)}/sitemap`, {
      ...cached(`${slugPath(slug)}/sitemap`), credentials: "omit", redirect: "error", headers: { Accept: "application/json" }, signal: AbortSignal.timeout(5000),
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error();
    const parsed = parseSitemap(await response.json());
    if (!parsed) throw new Error();
    return parsed;
  } catch {
    return unavailable();
  }
}

/** SC-043: an institutional page; the same seven tags as product descriptions, nothing else. */
export async function loadPage(slug: string, page: string): Promise<{ store: PublicStore; title: string; bodyHtml: string }> {
  if (page.length > 40 || !/^[a-z]+(?:-[a-z]+)*$/.test(page)) notFound();
  const input = object(await publicGet(`${slugPath(slug)}/pages/${encodeURIComponent(page)}`));
  if (typeof input.title !== "string" || [...input.title].length > 60 || typeof input.bodyHtml !== "string"
    || [...input.bodyHtml].length > 20000 || input.bodyHtml.replace(/<\/?(?:p|strong|em|ul|ol|li)>|<br\s*\/?>/g, "").includes("<")) unavailable();
  return { store: store(input.store, slug), title: input.title, bodyHtml: input.bodyHtml };
}

/** SC-045: one page of the catalog with filters, facets and menu, and the store it belongs to. */
export async function loadStoreCatalog(slug: string, query: URLSearchParams): Promise<{ store: PublicStore; catalog: CatalogPage }> {
  const input = object(await publicGet(`${slugPath(slug)}/catalog${query.size ? `?${query}` : ""}`));
  let catalog: CatalogPage;
  try { catalog = parseCatalog(input); } catch { return unavailable(); }
  return { store: store(input.store, slug), catalog };
}

/**
 * SC-043: the first cards of a home section. A section that fails (its category was hidden
 * meanwhile, the API is slow) is left out instead of taking the whole home down.
 */
export async function loadSectionCards(slug: string, query: URLSearchParams, limit: number): Promise<Card[]> {
  try {
    const response = await fetch(`${apiOrigin()}${slugPath(slug)}/catalog?${query}`, {
      ...cached(`${slugPath(slug)}/catalog`), credentials: "omit", redirect: "error", headers: { Accept: "application/json" }, signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) return [];
    return parseCatalog(await response.json()).products.slice(0, limit);
  } catch {
    return [];
  }
}

/** SC-045: a product by its storefront address, or the address an old link moved to. */
export async function loadProductBySlug(slug: string, productSlug: string): Promise<{ movedTo: string } | PublicProductDetail> {
  const path = slugPath(slug);
  if (productSlug.length > 80 || !SLUG.test(productSlug)) notFound();
  const input = object(await publicGet(`${path}/p/${encodeURIComponent(productSlug)}`));
  if (typeof input.movedTo === "string") {
    if (input.movedTo.length > 80 || !SLUG.test(input.movedTo)) unavailable();
    return { movedTo: input.movedTo };
  }
  const details = object(input.product);
  const id = object(details.product).id;
  if (typeof id !== "string" || !UUID.test(id)) unavailable();
  return detail(slug, details, await publicGet(`${path}/products/${encodeURIComponent(id)}/images`), id);
}

export async function loadCatalog(slug: string, after?: string, category?: string): Promise<PublicCatalog> {
  const path = slugPath(slug);
  if ((after !== undefined && !UUID.test(after)) || (category !== undefined && !UUID.test(category))) notFound();
  const query = new URLSearchParams();
  if (after) query.set("after", after);
  if (category) query.set("categoria", category);
  const input = object(await publicGet(path + (query.size ? `?${query}` : "")));
  if (!Array.isArray(input.products) || input.products.length > 20
    || (input.nextCursor !== null && (typeof input.nextCursor !== "string" || !UUID.test(input.nextCursor)))
    || !Array.isArray(input.categories) || input.categories.length > 50
    || (input.category !== null && input.category !== (category ?? null))) unavailable();
  // MVP block 1c: only categories with published products; project the public fields only.
  const categories = input.categories.map(value => {
    const item = object(value);
    if (typeof item.id !== "string" || !UUID.test(item.id) || [...name(item.name)].length > 60) unavailable();
    return { id: item.id, name: name(item.name) };
  });
  return { ...store(input, slug), categories, category: category ?? null, products: input.products.map(product), nextCursor: input.nextCursor };
}

export async function loadProduct(slug: string, id: string): Promise<PublicProductDetail> {
  const path = slugPath(slug);
  if (!UUID.test(id)) notFound();
  const [raw, rawImages] = await Promise.all([publicGet(`${path}/products/${encodeURIComponent(id)}`), publicGet(`${path}/products/${encodeURIComponent(id)}/images`)]);
  return detail(slug, object(raw), rawImages, id);
}

function detail(slug: string, input: Record<string, unknown>, rawImages: unknown, id: string): PublicProductDetail {
  const item = product(input.product);
  if (item.id.toLowerCase() !== id.toLowerCase()) unavailable();
  if (item.kind === "VARIABLE" && item.variants.length === 0) unavailable();
  const imageList = object(rawImages);
  if (!Array.isArray(imageList.images) || imageList.images.length > 6) unavailable();
  const images = imageList.images.map(value => {
    const image = object(value);
    if (typeof image.id !== "string" || !UUID.test(image.id) || typeof image.alt !== "string" || [...image.alt].length > 200
      || typeof image.width !== "number" || !Number.isInteger(image.width) || image.width < 1 || image.width > 4096
      || typeof image.height !== "number" || !Number.isInteger(image.height) || image.height < 1 || image.height > 4096) unavailable();
    return { id: image.id, alt: image.alt, width: image.width, height: image.height };
  });
  return { store: store(input.store, slug), product: item, images, extras: extras(object(input.product), input) };
}

/** SC-045: storefront details of the product page; a malformed one is an unavailable page. */
function extras(product: Record<string, unknown>, page: Record<string, unknown>): ProductExtras {
  try {
    const card = parseCard({ ...product, brand: null });
    const link = (value: unknown): Link => {
      const item = object(value);
      if (typeof item.slug !== "string" || item.slug.length > 80 || !SLUG.test(item.slug)) unavailable();
      return { name: name(item.name), slug: item.slug };
    };
    const variantCompareAt: Record<string, string> = {};
    for (const value of Array.isArray(product.variants) ? product.variants : []) {
      const variant = object(value);
      if (typeof variant.compareAtPrice === "string" && /^\d{1,17}\.\d{2}$/.test(variant.compareAtPrice) && typeof variant.id === "string")
        variantCompareAt[variant.id] = variant.compareAtPrice;
    }
    return {
      slug: card.slug,
      brand: product.brand == null ? null : link(product.brand),
      categoryPath: Array.isArray(product.categoryPath) && product.categoryPath.length <= 3 ? product.categoryPath.map(link) : [],
      compareAtPrice: card.compareAtPrice, discountPercent: card.discountPercent, variantCompareAt,
      related: Array.isArray(page.related) && page.related.length <= 8 ? page.related.map(parseCard) : [],
      maxInstallments: parseInstallments(page.maxInstallments),
    };
  } catch {
    return unavailable();
  }
}

/** The store logo through this app's own origin, like product photos (MVP block 1b). */
export async function publicLogo(slug: string): Promise<Response> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug)) return new Response(null, { status: 404 });
  return proxyImage(`${apiOrigin()}/api/public/stores/${encodeURIComponent(slug)}/logo`);
}

/**
 * SC-047: a photo by its id never changes (a new upload is a new id), so browsers and this
 * server's image optimizer keep it for a day; "primary", the logo and banners can change under the
 * same address and are kept for 60 s only.
 */
const IMMUTABLE = "public, max-age=86400, immutable";
const SHORT = "public, max-age=60";

async function proxyImage(url: string, cacheControl = SHORT): Promise<Response> {
  try {
    const response = await fetch(url, {
      credentials: "omit", cache: "no-store", redirect: "error", headers: { Accept: "image/jpeg, image/png" }, signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) return new Response(null, { status: response.status === 404 ? 404 : 503, headers: { "Cache-Control": "no-store" } });
    const type = response.headers.get("content-type")?.split(";")[0];
    if (type !== "image/png" && type !== "image/jpeg") throw new Error();
    return new Response(response.body, { status: 200, headers: { "Content-Type": type, "Cache-Control": cacheControl, "X-Content-Type-Options": "nosniff" } });
  } catch { return new Response(null, { status: 503, headers: { "Cache-Control": "no-store" } }); }
}

/** SC-043: a home banner image; the phone variant falls back to the desktop one in the API. */
export async function publicBanner(slug: string, bannerId: string, variant: string): Promise<Response> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug) || !UUID.test(bannerId) || (variant !== "desktop" && variant !== "mobile"))
    return new Response(null, { status: 404 });
  return proxyImage(`${apiOrigin()}/api/public/stores/${encodeURIComponent(slug)}/banners/${encodeURIComponent(bannerId)}/images/${variant}`);
}

export async function publicImage(slug: string, productId: string, imageId: string): Promise<Response> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug) || !UUID.test(productId) || (imageId !== "primary" && !UUID.test(imageId))) return new Response(null, { status: 404 });
  return proxyImage(`${apiOrigin()}/api/public/stores/${encodeURIComponent(slug)}/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/content`,
    imageId === "primary" ? SHORT : IMMUTABLE);
}
