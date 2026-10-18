"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import SideNav, { accountItems } from "@/components/side-nav";

export default function AccountShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return <div className="app-shell">
    <SideNav label="Navegação da conta" title="Sua conta" items={accountItems(pathname)} />
    <main id="conteudo" className="app-main" tabIndex={-1}>{children}</main>
  </div>;
}
