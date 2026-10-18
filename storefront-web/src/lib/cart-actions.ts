"use server";

import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { addLine, cartCookieName, cartCookieOptions, type CartLine, parseCart, removeLine, serializeCart, setLine, validSlug, validVariantId } from "@/lib/cart-cookie";

// Server actions run on the storefront origin; Next rejects cross-origin action posts.
// The cookie stays untrusted: every price and availability comes from the backend quote.

function slugFrom(form: FormData): string {
  const slug = form.get("slug");
  if (typeof slug !== "string" || !validSlug(slug)) notFound();
  return slug;
}

function cartPath(slug: string, notice?: string): string {
  return `/lojas/${encodeURIComponent(slug)}/carrinho${notice ? `?aviso=${notice}` : ""}`;
}

function quantityFrom(form: FormData): number | null {
  const value = form.get("quantity");
  return typeof value === "string" && /^(?:[1-9]|10)$/.test(value) ? Number(value) : null;
}

async function read(slug: string): Promise<CartLine[]> {
  return parseCart((await cookies()).get(cartCookieName(slug))?.value);
}

async function write(slug: string, lines: CartLine[]): Promise<void> {
  const jar = await cookies();
  const options = cartCookieOptions(slug, process.env.NODE_ENV !== "development");
  if (lines.length === 0) jar.delete({ name: cartCookieName(slug), path: options.path });
  else jar.set(cartCookieName(slug), serializeCart(lines), options);
}

export async function addToCart(form: FormData): Promise<void> {
  const slug = slugFrom(form);
  const variantId = form.get("variantId");
  const quantity = quantityFrom(form);
  if (!validVariantId(variantId) || quantity === null) redirect(cartPath(slug, "invalido"));
  const next = addLine(await read(slug), variantId, quantity);
  if (!next) redirect(cartPath(slug, "limite"));
  await write(slug, next);
  redirect(cartPath(slug, "adicionado"));
}

export async function updateCartItem(form: FormData): Promise<void> {
  const slug = slugFrom(form);
  const variantId = form.get("variantId");
  const quantity = quantityFrom(form);
  if (!validVariantId(variantId) || quantity === null) redirect(cartPath(slug, "invalido"));
  await write(slug, setLine(await read(slug), variantId, quantity));
  redirect(cartPath(slug, "atualizado"));
}

export async function removeCartItem(form: FormData): Promise<void> {
  const slug = slugFrom(form);
  const variantId = form.get("variantId");
  if (!validVariantId(variantId)) redirect(cartPath(slug, "invalido"));
  await write(slug, removeLine(await read(slug), variantId));
  redirect(cartPath(slug, "removido"));
}
