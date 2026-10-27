"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import AppFrame, { accountItems } from "@/components/app-frame";

export default function AccountShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return <AppFrame navLabel="Navegação da conta" context={<><span className="block text-xs font-normal text-muted-foreground">Área</span> Sua conta</>}
    groups={[{ label: "Conta", items: accountItems(pathname) }]}>{children}</AppFrame>;
}
