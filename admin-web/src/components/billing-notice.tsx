"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { billingNotice, parseAccount, type BillingAccount } from "@/lib/billing";
import { BILLING_CHANGED } from "@/lib/session-events";

// SC-033 (ADR-0017 B7): the trial's last days, a failed charge or an inactive account, on every panel
// page. Asked again when a write is refused with 402, so the notice appears as soon as it matters.
// Silent on any failure: the pages keep their own messages.
export default function BillingNotice() {
  const pathname = usePathname();
  const [account, setAccount] = useState<BillingAccount | null>(null);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const listener = () => setRefresh(current => current + 1);
    window.addEventListener(BILLING_CHANGED, listener);
    return () => window.removeEventListener(BILLING_CHANGED, listener);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/billing/account", { credentials: "same-origin", cache: "no-store", signal: controller.signal })
      .then(async response => setAccount(response.ok ? parseAccount(await response.json()) : null))
      .catch(() => { if (!controller.signal.aborted) setAccount(null); });
    return () => controller.abort();
  }, [refresh]);

  const notice = account ? billingNotice(account, new Date()) : null;
  if (!notice || pathname === "/conta/plano") return null;
  // Static on purpose: it is on almost every page, and a live region would repeat it on each navigation.
  return <p className={notice.tone === "danger" ? "billing-notice feedback feedback-error" : "billing-notice notice"}>
    {notice.text} <Link href="/conta/plano">Ver plano e cobrança</Link>
  </p>;
}
