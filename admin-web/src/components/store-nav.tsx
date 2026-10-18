"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";

// Navigation only links to the route's tenant; the backend still authorizes every request.
export default function StoreNav({ tenantId }: { tenantId: string }) {
  const pathname = usePathname();
  const [storeName, setStoreName] = useState("");
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === pathname;
  const base = `/lojas/${encodeURIComponent(tenantId)}`;
  const items = [
    { href: base, label: "Visão geral", current: pathname === base },
    { href: `${base}/produtos`, label: "Produtos", current: pathname.startsWith(`${base}/produtos`) },
    { href: "/conta", label: "Minha conta", current: false },
  ];

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal })
      .then(async response => { if (response.ok) setStoreName(((await response.json()) as TenantStore).store.name); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);

  return <nav className="store-nav" aria-label="Navegação da loja">
    <div className="store-nav-top">
      <p className="store-nav-name">{storeName ? <><span className="store-nav-label">Loja</span> <span translate="no">{storeName}</span></> : "Sua loja"}</p>
      <button type="button" className="store-nav-toggle" aria-expanded={open} aria-controls="store-nav-items" onClick={() => setOpenAt(open ? null : pathname)}>Menu</button>
    </div>
    <ul id="store-nav-items" className="store-nav-items" data-open={open}>
      {items.map(item => <li key={item.href}><Link href={item.href} aria-current={item.current ? "page" : undefined}>{item.label}</Link></li>)}
    </ul>
  </nav>;
}
