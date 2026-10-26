import "server-only";
import { notFound } from "next/navigation";

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
export type PublicCatalog = PublicStore & { products: PublicProduct[]; nextCursor: string | null };
export type PublicProductDetail = { store: PublicStore; product: PublicProduct; images: PublicImage[] };

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

async function publicGet(path: string): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${path}`, {
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(5000),
    });
  } catch {
    return unavailable();
  }
  if (response.status === 404) notFound();
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

export async function loadCatalog(slug: string, after?: string): Promise<PublicCatalog> {
  const path = slugPath(slug);
  if (after !== undefined && !UUID.test(after)) notFound();
  const input = object(await publicGet(path + (after ? `?after=${encodeURIComponent(after)}` : "")));
  if (!Array.isArray(input.products) || input.products.length > 20
    || (input.nextCursor !== null && (typeof input.nextCursor !== "string" || !UUID.test(input.nextCursor)))) unavailable();
  return { ...store(input, slug), products: input.products.map(product), nextCursor: input.nextCursor };
}

export async function loadProduct(slug: string, id: string): Promise<PublicProductDetail> {
  const path = slugPath(slug);
  if (!UUID.test(id)) notFound();
  const [raw, rawImages] = await Promise.all([publicGet(`${path}/products/${encodeURIComponent(id)}`), publicGet(`${path}/products/${encodeURIComponent(id)}/images`)]);
  const input = object(raw);
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
  return { store: store(input.store, slug), product: item, images };
}

/** The store logo through this app's own origin, like product photos (MVP block 1b). */
export async function publicLogo(slug: string): Promise<Response> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug)) return new Response(null, { status: 404 });
  return proxyImage(`${apiOrigin()}/api/public/stores/${encodeURIComponent(slug)}/logo`);
}

async function proxyImage(url: string): Promise<Response> {
  try {
    const response = await fetch(url, {
      credentials: "omit", cache: "no-store", redirect: "error", headers: { Accept: "image/jpeg, image/png" }, signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) return new Response(null, { status: response.status === 404 ? 404 : 503, headers: { "Cache-Control": "no-store" } });
    const type = response.headers.get("content-type")?.split(";")[0];
    if (type !== "image/png" && type !== "image/jpeg") throw new Error();
    return new Response(response.body, { status: 200, headers: { "Content-Type": type, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
  } catch { return new Response(null, { status: 503, headers: { "Cache-Control": "no-store" } }); }
}

export async function publicImage(slug: string, productId: string, imageId: string): Promise<Response> {
  if (slug.length < 3 || slug.length > 63 || !SLUG.test(slug) || !UUID.test(productId) || (imageId !== "primary" && !UUID.test(imageId))) return new Response(null, { status: 404 });
  try {
    const response = await fetch(`${apiOrigin()}/api/public/stores/${encodeURIComponent(slug)}/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/content`, {
      credentials: "omit", cache: "no-store", redirect: "error", headers: { Accept: "image/jpeg, image/png" }, signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) return new Response(null, { status: response.status === 404 ? 404 : 503, headers: { "Cache-Control": "no-store" } });
    const type = response.headers.get("content-type")?.split(";")[0];
    if (type !== "image/png" && type !== "image/jpeg") throw new Error();
    return new Response(response.body, { status: 200, headers: { "Content-Type": type, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
  } catch { return new Response(null, { status: 503, headers: { "Cache-Control": "no-store" } }); }
}
