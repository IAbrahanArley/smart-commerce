import "server-only";
import type { Metadata } from "next";
import type { PublicProductDetail } from "@/lib/public-store";
import { productPath } from "@/lib/catalog-page";
import { categoryPath } from "@/lib/menu";
import { breadcrumbLd, plainText, productLd, storePath } from "@/lib/seo";
import { siteOrigin, storeMetadata } from "@/lib/store-metadata";

// SC-047: title, description, canonical, Open Graph image and structured data of a product page.
// The canonical address is always /p/{endereço}, also when the page is opened by the original id.

function canonical(detail: PublicProductDetail): string {
  return productPath(detail.store.slug, { id: detail.product.id, slug: detail.extras.slug });
}

function photo(detail: PublicProductDetail, id: string): string {
  return `/midia/lojas/${encodeURIComponent(detail.store.slug)}/produtos/${encodeURIComponent(detail.product.id)}/${encodeURIComponent(id)}`;
}

export function productMetadata(detail: PublicProductDetail): Metadata {
  const description = plainText(detail.product.descriptionHtml) || `${detail.product.name} na loja ${detail.store.name}.`;
  return storeMetadata({
    title: `${detail.product.name} | ${detail.store.name}`,
    description,
    path: canonical(detail),
    image: detail.images[0] ? photo(detail, detail.images[0].id) : null,
  });
}

/** Product and breadcrumb JSON-LD, or nothing without the public origin. */
export function productStructuredData(detail: PublicProductDetail): unknown[] {
  const origin = siteOrigin();
  if (!origin) return [];
  const { store, product, images, extras } = detail;
  const path = canonical(detail);
  const variants = product.variants.length
    ? product.variants.map(variant => ({ name: variant.options.length ? variant.options.map(option => option.value).join(" / ") : null, price: variant.price, available: variant.available }))
    : [{ name: null, price: product.price, available: product.available }];
  return [
    productLd(origin, {
      name: product.name, description: plainText(product.descriptionHtml, 5000), path, images: images.map(image => photo(detail, image.id)),
      brand: extras.brand?.name ?? null, currency: product.currency, variants,
    }),
    breadcrumbLd(origin, [
      { name: store.name, path: storePath(store.slug) },
      ...extras.categoryPath.map(item => ({ name: item.name, path: categoryPath(store.slug, item.slug) })),
      { name: product.name, path },
    ]),
  ];
}
