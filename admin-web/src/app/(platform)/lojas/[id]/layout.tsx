import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import StoreFrame from "@/components/store-frame";
import { validRouteId } from "@/lib/route-ids";

export default async function StoreLayout({ children, params }: { children: ReactNode; params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!validRouteId(id)) notFound();
  return <StoreFrame key={id} tenantId={id}>{children}</StoreFrame>;
}
