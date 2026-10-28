import "server-only";
import type { Metadata } from "next";
import { indexable, publicOrigin } from "@/lib/seo";

// SC-047 (ADR-0018 V11): metadata of the store pages. Canonical and Open Graph addresses need the
// public origin (STOREFRONT_PUBLIC_ORIGIN); without it they are left out instead of pointing at a
// guessed host. The hosted test environment is never indexed.

export function siteOrigin(): string | null {
  return publicOrigin(process.env.STOREFRONT_PUBLIC_ORIGIN);
}

export function storeMetadata({ title, description, path, image, index = true }: {
  title: string; description: string; path: string; image?: string | null; index?: boolean;
}): Metadata {
  const origin = siteOrigin();
  const robots = index && indexable(process.env.DEPLOYMENT_ENVIRONMENT) ? { index: true, follow: true } : { index: false, follow: true };
  return {
    // The store's own name, not the platform template.
    title: { absolute: title },
    description,
    robots,
    ...(origin ? {
      alternates: { canonical: `${origin}${path}` },
      openGraph: { type: "website", locale: "pt_BR", title, description, url: `${origin}${path}`, ...(image ? { images: [{ url: `${origin}${image}` }] } : {}) },
    } : {}),
  };
}
