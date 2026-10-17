import type { Metadata } from "next";
import AccountPanel from "@/components/account";

export const metadata: Metadata = { title: "Minha conta | Smart Commerce", robots: { index: false, follow: false } };
export default function AccountPage() { return <AccountPanel />; }
