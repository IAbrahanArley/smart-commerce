import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { StoreFrame } from "@/components/store-frame";
import { Arrow } from "@/components/arrow";
import { OrderLookupForm } from "@/components/order-lookup-form";
import { StoreContacts } from "@/components/store-contacts";
import { checkoutConfigured } from "@/lib/checkout-input";
import { loadCatalog } from "@/lib/public-store";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Acompanhar pedido", robots: { index: false, follow: false } };

type Props = { params: Promise<{ slug: string }> };

// SC-046 (ADR-0018 V10): any device can follow an order with its number and the buyer e-mail.
// Exists only where the checkout exists (D6).
export default async function OrderLookupPage({ params }: Props) {
  if (!checkoutConfigured()) notFound();
  const { slug } = await params;
  const store = await loadCatalog(slug);
  return (
    <StoreFrame store={store}>
      <main id="conteudo" className="page-width order-page lookup-page" tabIndex={-1}>
        <nav className="breadcrumb" aria-label="Caminho de navegação">
          <Link href={`/lojas/${encodeURIComponent(store.slug)}`} prefetch={false}><Arrow back /> Voltar à loja</Link>
        </nav>
        <h1>Acompanhar pedido</h1>
        <p className="lookup-intro">Informe o número do pedido e o e-mail usado na compra para ver a situação, os itens e a entrega.</p>
        <OrderLookupForm slug={store.slug} />
        <StoreContacts store={store} />
      </main>
    </StoreFrame>
  );
}
