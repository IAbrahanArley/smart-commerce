import type { Metadata } from "next";
import AccountPanel from "@/components/account";
import AccountShell from "@/components/account-shell";

export const metadata: Metadata = { title: "Minha conta | Smart Commerce", robots: { index: false, follow: false } };
export default function AccountPage() { return <AccountShell><AccountPanel /></AccountShell>; }
