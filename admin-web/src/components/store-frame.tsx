"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";
import AppFrame from "@/components/app-frame";
import { accountItems, staffItems, storeItems } from "@/lib/navigation";

// Navigation only links to the route's tenant; the backend still authorizes every request.
export default function StoreFrame({ tenantId, children }: { tenantId: string; children: ReactNode }) {
  const pathname = usePathname();
  const [storeName, setStoreName] = useState("");
  const [staff, setStaff] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal })
      .then(async response => {
        if (!response.ok) return;
        const tenant = (await response.json()) as TenantStore;
        setStoreName(tenant.store.name); setStaff(tenant.role === "STAFF");
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);

  return <AppFrame navLabel="Navegação da loja" tenantId={tenantId}
    context={<><span className="block text-xs font-normal text-muted-foreground">Loja</span> {storeName ? <span translate="no">{storeName}</span> : "Sua loja"}</>}
    groups={[staff ? { label: "Equipe da loja", items: staffItems(tenantId, pathname) } : { label: "Gestão da loja", items: storeItems(tenantId, pathname) }, { label: "Conta", items: accountItems(pathname) }]}>{children}</AppFrame>;
}
