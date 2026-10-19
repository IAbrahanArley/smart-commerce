"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";
import SideNav, { accountItems } from "@/components/side-nav";

// Navigation only links to the route's tenant; the backend still authorizes every request.
export default function StoreNav({ tenantId }: { tenantId: string }) {
  const pathname = usePathname();
  const [storeName, setStoreName] = useState("");
  const base = `/lojas/${encodeURIComponent(tenantId)}`;
  const items = [
    { href: base, label: "Visão geral", current: pathname === base },
    { href: `${base}/produtos`, label: "Produtos", current: pathname.startsWith(`${base}/produtos`) },
    { href: `${base}/pedidos`, label: "Pedidos", current: pathname.startsWith(`${base}/pedidos`) },
    { href: `${base}/entrega`, label: "Entrega", current: pathname === `${base}/entrega` },
  ];

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal })
      .then(async response => { if (response.ok) setStoreName(((await response.json()) as TenantStore).store.name); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);

  return <SideNav label="Navegação da loja" items={items} secondary={accountItems(pathname)}
    title={storeName ? <><span className="side-nav-label">Loja</span> <span translate="no">{storeName}</span></> : "Sua loja"} />;
}
