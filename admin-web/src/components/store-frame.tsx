"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";
import AppFrame, { accountItems } from "@/components/app-frame";

// Navigation only links to the route's tenant; the backend still authorizes every request.
export default function StoreFrame({ tenantId, children }: { tenantId: string; children: ReactNode }) {
  const pathname = usePathname();
  const [storeName, setStoreName] = useState("");
  const base = `/lojas/${encodeURIComponent(tenantId)}`;
  const items = [
    { href: base, label: "Visão geral", icon: "home", current: pathname === base },
    { href: `${base}/produtos`, label: "Produtos", icon: "box", current: pathname.startsWith(`${base}/produtos`) },
    { href: `${base}/pedidos`, label: "Pedidos", icon: "receipt", current: pathname.startsWith(`${base}/pedidos`) },
    { href: `${base}/entrega`, label: "Entrega", icon: "truck", current: pathname === `${base}/entrega` },
    { href: `${base}/pagamentos`, label: "Pagamentos", icon: "card", current: pathname === `${base}/pagamentos` },
  ] as const;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal })
      .then(async response => { if (response.ok) setStoreName(((await response.json()) as TenantStore).store.name); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);

  return <AppFrame navLabel="Navegação da loja"
    context={<><span className="app-sidebar-context-label">Loja</span> {storeName ? <span translate="no">{storeName}</span> : "Sua loja"}</>}
    groups={[{ label: "Gestão da loja", items: [...items] }, { label: "Conta", items: accountItems(pathname) }]}>{children}</AppFrame>;
}
