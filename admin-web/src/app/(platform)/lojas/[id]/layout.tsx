import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import StoreNav from "@/components/store-nav";
import { validRouteId } from "@/lib/route-ids";

export default async function StoreLayout({ children, params }: { children: ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!validRouteId(id)) notFound();
  return <div className="app-shell">
    <StoreNav key={id} tenantId={id} />
    <main id="conteudo" className="app-main" tabIndex={-1}>{children}</main>
  </div>;
}
