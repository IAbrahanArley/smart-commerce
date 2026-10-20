"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { Product } from "@/lib/catalog";
import { ordersApiPath, ordersPagePath, type OrderPage } from "@/lib/orders";
import Icon, { type IconName } from "@/components/icons";

type Summary = { products: Product[] | null; pending: OrderPage | null };
const count = (value: number) => new Intl.NumberFormat("pt-BR").format(value);

// Overview cards (DESIGN.md: Cards de resumo). Only figures the existing APIs return; nothing is
// estimated. The orders API pages by 20, so a next page means "more than" the first page holds.
export default function StoreSummary({ tenantId, published }: { tenantId: string; published: boolean }) {
  const [summary, setSummary] = useState<Summary | null>(null);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    const read = <T,>(path: string) => apiRequest(path, { signal: controller.signal })
      .then(async response => response.ok ? (await response.json()) as T : null).catch(() => null);
    void Promise.all([read<Product[]>(`/api/tenants/${encodeURIComponent(tenantId)}/products`), read<OrderPage>(ordersApiPath(tenantId, "PENDING_PAYMENT", null))])
      .then(([products, pending]) => { if (!controller.signal.aborted) setSummary({ products, pending }); });
    return () => controller.abort();
  }, [tenantId]);

  const products = summary?.products;
  const selected = products?.filter(product => product.published).length ?? 0;
  const pending = summary?.pending;
  const pendingText = !pending ? "Indisponível" : pending.nextCursor ? `Mais de ${count(pending.orders.length)}` : count(pending.orders.length);

  return <section className="stat-grid" aria-labelledby="store-summary-title" aria-busy={!summary}>
    <h2 id="store-summary-title" className="visually-hidden">Resumo da loja</h2>
    <Card icon="eye" label="Vitrine" value={published ? "Publicada" : "Oculta"}
      detail={published ? "Visível para quem tem o link." : "Ninguém vê a loja ainda."} href="#publication-title" linkText="Ver publicação" />
    <Card icon="box" label="Produtos cadastrados" value={!summary ? "…" : products ? count(products.length) : "Indisponível"}
      detail={products ? `${count(selected)} ${selected === 1 ? "selecionado" : "selecionados"} para a vitrine` : "Consulte a lista de produtos."} href={`${storePath}/produtos`} linkText="Gerenciar produtos" />
    <Card icon="receipt" label="Aguardando pagamento" value={!summary ? "…" : pendingText}
      detail="Pedidos que reservam estoque por 30 minutos." href={ordersPagePath(tenantId, "PENDING_PAYMENT", null)} linkText="Ver pedidos" />
  </section>;
}

function Card({ icon, label, value, detail, href, linkText }: { icon: IconName; label: string; value: string; detail: string; href: string; linkText: string }) {
  return <div className="stat-card">
    <span className="stat-icon"><Icon name={icon} /></span>
    <div className="stat-body">
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      <p className="stat-detail">{detail}</p>
      {href.startsWith("#") ? <a href={href}>{linkText}</a> : <Link href={href}>{linkText}</Link>}
    </div>
  </div>;
}
