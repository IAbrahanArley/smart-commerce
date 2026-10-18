import type { Metadata } from "next";
import AccountShell from "@/components/account-shell";
import StoresPanel from "@/components/stores";

export const metadata: Metadata = { title: "Minhas lojas | Smart Commerce", robots: { index: false, follow: false } };
export default function StoresPage() { return <AccountShell><StoresPanel /></AccountShell>; }
