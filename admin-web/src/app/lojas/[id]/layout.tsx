import type { ReactNode } from "react";
import StoreNav from "@/components/store-nav";

export default async function StoreLayout({ children, params }: { children: ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <div className="app-shell">
    <StoreNav key={id} tenantId={id} />
    <main id="conteudo" className="app-main" tabIndex={-1}>{children}</main>
  </div>;
}
