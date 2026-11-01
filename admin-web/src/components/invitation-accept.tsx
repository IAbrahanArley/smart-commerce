"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { invitationToken, staffRefusal } from "@/lib/staff";
import { Button } from "@/components/ui/button";

type State =
  | { kind: "checking" } | { kind: "invalid" } | { kind: "signed-out" } | { kind: "ready"; email: string }
  | { kind: "done"; tenantId: string; storeName: string } | { kind: "error"; text: string };

// SC-049: the invited person accepts the store invitation. The token comes from the link's fragment,
// is kept only in memory and removed from the address bar; acceptance needs an explicit click and
// the signed-in account with the invited e-mail (checked by the backend).
export default function InvitationAccept() {
  const token = useRef<string | null>(null);
  const read = useRef(false);
  const [state, setState] = useState<State>({ kind: "checking" });
  const [busy, setBusy] = useState(false);
  const message = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    // Read once: the effect may run twice (development), and the fragment is gone after the first run.
    if (!read.current) {
      read.current = true;
      token.current = invitationToken(window.location.hash);
      if (window.location.hash) window.history.replaceState(null, "", window.location.pathname);
    }
    if (!token.current) { setState({ kind: "invalid" }); return; }
    const controller = new AbortController();
    apiRequest("/api/auth/me", { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState({ kind: "signed-out" }); return; }
      if (!response.ok) throw new Error();
      const me = (await response.json()) as { email?: unknown };
      setState({ kind: "ready", email: typeof me.email === "string" ? me.email : "" });
    }).catch(() => { if (!controller.signal.aborted) setState({ kind: "error", text: "Não foi possível verificar sua sessão. Recarregue a página." }); });
    return () => controller.abort();
  }, []);

  useEffect(() => { if (state.kind !== "checking") message.current?.focus(); }, [state.kind]);

  async function accept() {
    if (!token.current || busy) return;
    setBusy(true);
    try {
      const response = await apiRequest("/api/staff-invitations/acceptance", { method: "POST", body: { token: token.current } });
      const body: unknown = await response.json().catch(() => null);
      if (response.status === 401) { setState({ kind: "signed-out" }); return; }
      if (!response.ok) { setState({ kind: "error", text: staffRefusal(response.status, body) ?? requestError(response.status) }); return; }
      const value = (body ?? {}) as { tenantId?: unknown; storeName?: unknown };
      if (typeof value.tenantId !== "string" || typeof value.storeName !== "string") throw new Error();
      token.current = null;
      setState({ kind: "done", tenantId: value.tenantId, storeName: value.storeName });
    } catch {
      setState({ kind: "error", text: "Não foi possível aceitar o convite agora. Tente novamente." });
    } finally { setBusy(false); }
  }

  const heading = (text: string) => <h1 ref={message} tabIndex={-1} className="m-0 text-2xl font-bold">{text}</h1>;
  return <section className="flex w-full max-w-md flex-col gap-4" aria-busy={state.kind === "checking" || busy}>
    {state.kind === "checking" ? <p role="status">Verificando o convite…</p> : null}
    {state.kind === "invalid" ? <>{heading("Link de convite incompleto")}
      <p className="m-0">Abra o link exatamente como chegou no e-mail. Se ele não funcionar, peça um novo convite ao dono da loja.</p></> : null}
    {state.kind === "signed-out" ? <>{heading("Entre para aceitar o convite")}
      <p className="m-0">Entre com a conta do e-mail que recebeu o convite (ou crie a conta com esse e-mail). Depois, abra de novo o link do e-mail.</p>
      <div className="flex flex-wrap gap-3"><Button asChild><Link href="/entrar">Entrar</Link></Button><Button asChild variant="outline"><Link href="/cadastro">Criar conta</Link></Button></div></> : null}
    {state.kind === "ready" ? <>{heading("Convite para a equipe da loja")}
      <p className="m-0">Você vai poder operar o caixa e registrar vendas desta loja. Você não verá relatórios, custos nem contas da loja.</p>
      {state.email ? <p className="m-0 text-sm text-muted-foreground">Conta: <strong className="text-foreground">{state.email}</strong>. O convite só vale para o e-mail convidado.</p> : null}
      <div><Button type="button" disabled={busy} onClick={() => void accept()}>{busy ? "Aceitando…" : "Aceitar convite"}</Button></div></> : null}
    {state.kind === "done" ? <>{heading(`Você agora faz parte da equipe da loja ${state.storeName}`)}
      <div><Button asChild><Link href={`/lojas/${encodeURIComponent(state.tenantId)}`}>Ir para a loja</Link></Button></div></> : null}
    {state.kind === "error" ? <>{heading("Não foi possível aceitar o convite")}
      <p role="alert" className="feedback feedback-error m-0">{state.text}</p>
      <Link href="/lojas">Ir para minhas lojas</Link></> : null}
  </section>;
}
