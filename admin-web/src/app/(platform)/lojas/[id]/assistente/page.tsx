import type { Metadata } from "next";
import StoreAdvisor from "@/components/store-advisor";
import { explainQuestion } from "@/lib/advisor";

export const metadata: Metadata = { title: "Assistente | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ explicar?: string | string[]; periodo?: string | string[] }> };

// The conversation lives only in this page (ADR-0016 A7): leaving or reloading starts a new one.
// SC-028: ?explicar= names a known topic, turned here into a fixed question; anything else opens
// the page empty.
export default async function AdvisorPage({ params, searchParams }: Props) {
  const { id } = await params;
  const { explicar, periodo } = await searchParams;
  const question = explainQuestion(typeof explicar === "string" ? explicar : undefined, typeof periodo === "string" ? periodo : undefined);
  // Keyed by store only: dropping ?explicar= after asking must not remount the conversation.
  return <StoreAdvisor key={id} tenantId={id} initialQuestion={question} />;
}
