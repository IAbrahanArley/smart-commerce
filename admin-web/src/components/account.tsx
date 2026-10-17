"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { authRequest, requestError } from "@/lib/auth-api";
import StoresPanel from "@/components/stores";

type Account = { id: string; email: string };

export default function AccountPanel() {
  const router = useRouter();
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [signedOut, setSignedOut] = useState(false);
  const [pending, setPending] = useState(false);
  const submitting = useRef(false);
  const feedback = useRef<HTMLParagraphElement>(null);
  const sessionExpired = useCallback(() => { setAccount(null); setSignedOut(true); }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", signal: controller.signal })
      .then(async response => {
        if (response.status === 401) { setSignedOut(true); return; }
        if (!response.ok) throw new Error();
        setAccount(await response.json());
      })
      .catch(() => { if (!controller.signal.aborted) setError("Não foi possível consultar a conta. Recarregue a página para tentar novamente."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  useEffect(() => { if (error) feedback.current?.focus(); }, [error]);

  async function logout() {
    if (submitting.current) return;
    submitting.current = true;
    setPending(true);
    setError("");
    try {
      const response = await authRequest("logout");
      if (!response.ok) { setError(requestError(response.status)); return; }
      setAccount(null);
      router.replace("/entrar");
      router.refresh();
    } catch { setError("Não foi possível sair. Verifique a conexão e tente novamente."); }
    finally { submitting.current = false; setPending(false); }
  }

  return <div className="account-layout"><section className="account-summary" aria-labelledby="account-title">
    <h1 id="account-title">Minha conta</h1>
    <p ref={feedback} role="status" tabIndex={-1} aria-live="polite" className={error ? "feedback feedback-error" : "feedback-empty"}>{error}</p>
    {loading ? <p role="status">Consultando sua conta…</p> : account ? <>
      <p>Você está conectado ao Smart Commerce.</p>
      <dl><dt>E-mail</dt><dd className="account-email">{account.email}</dd></dl>
      <button type="button" onClick={logout} disabled={pending}>{pending ? "Saindo…" : "Sair da conta"}</button>
    </> : signedOut ? <><p>Entre para consultar sua conta. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
  </section>{account ? <StoresPanel onSessionExpired={sessionExpired} /> : null}</div>;
}
