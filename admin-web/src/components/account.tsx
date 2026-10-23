"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useSignOut } from "@/lib/use-sign-out";
import { apiRequest } from "@/lib/auth-api";

type Account = { id: string; email: string };

export default function AccountPanel() {
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [signedOut, setSignedOut] = useState(false);
  const { signOut, pending, error: signOutError } = useSignOut();
  const error = loadError || signOutError;
  const feedback = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/api/auth/me", { signal: controller.signal })
      .then(async response => {
        if (response.status === 401) { setSignedOut(true); return; }
        if (!response.ok) throw new Error();
        setAccount(await response.json());
      })
      .catch(() => { if (!controller.signal.aborted) setLoadError("Não foi possível consultar a conta. Recarregue a página para tentar novamente."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  useEffect(() => { if (error) feedback.current?.focus(); }, [error]);

  return <section className="account-summary" aria-labelledby="account-title">
    <h1 id="account-title">Minha conta</h1>
    <p ref={feedback} role="status" tabIndex={-1} aria-live="polite" className={error ? "feedback feedback-error" : "feedback-empty"}>{error}</p>
    {loading ? <p role="status">Consultando sua conta…</p> : account ? <>
      <p>Você está conectado ao Smart Commerce. Suas lojas ficam em <Link href="/lojas">Minhas lojas</Link>.</p>
      <dl><dt>E-mail</dt><dd className="account-email">{account.email}</dd></dl>
      <button type="button" onClick={() => void signOut()} disabled={pending}>{pending ? "Saindo…" : "Sair da conta"}</button>
    </> : signedOut ? <><p>Entre para consultar sua conta. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
  </section>;
}
