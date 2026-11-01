import type { Metadata } from "next";
import StoreStaff from "@/components/store-staff";

export const metadata: Metadata = { title: "Funcionários | Smart Commerce", robots: { index: false, follow: false } };

export default async function StaffPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreStaff key={id} tenantId={id} />;
}
