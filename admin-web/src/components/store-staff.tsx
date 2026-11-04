"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { dateText } from "@/lib/billing";
import { inviteEmailError, parseStaff, staffLimitText, staffRefusal, type StaffOverview } from "@/lib/staff";
import PageHeader from "@/components/page-header";
import { AddButton, FormSheet } from "@/components/form-sheet";
import PageState from "@/components/page-state";
import { useConfirm } from "@/components/confirm-dialog";
import { Loading } from "@/components/ui/loading";

// SC-049 (ADR-0019 C1): the OWNER invites employees by e-mail, cancels open invitations and revokes
// access. Employees will operate the cash register and record in-person sales (next tasks); they
// never see reports, costs or payables. The backend enforces the role and the plan limit.
export default function StoreStaff({ tenantId }: { tenantId: string }) {
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/staff`;
  const [overview, setOverview] = useState<StaffOverview | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const { confirm, dialog } = useConfirm();

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404) { setState("missing"); return; }
      const parsed = response.ok ? parseStaff(await response.json()) : null;
      if (!parsed) throw new Error();
      setOverview(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function mutate(request: () => Promise<Response>, success: string) {
    setBusy(true); setFeedback(null);
    try {
      const response = await request();
      if (!response.ok) {
        const body: unknown = await response.json().catch(() => null);
        setFeedback({ text: staffRefusal(response.status, body) ?? requestError(response.status), error: true });
        return false;
      }
      setFeedback({ text: success, error: false });
      setAttempt(current => current + 1);
      return true;
    } catch {
      setFeedback({ text: "Não foi possível falar com o servidor. Tente novamente.", error: true });
      return false;
    } finally { setBusy(false); }
  }

  async function invite(event: FormEvent) {
    event.preventDefault();
    const problem = inviteEmailError(email);
    if (problem) { setEmailError(problem); emailRef.current?.focus(); return; }
    const value = email.trim();
    if (await mutate(() => apiRequest(`${api}/invitations`, { method: "POST", body: { email: value } }), `Convite enviado para ${value}. Ele vale por 7 dias.`)) { setEmail(""); setInviting(false); }
  }

  async function revoke(member: StaffOverview["members"][number]) {
    if (!(await confirm({ title: `Remover ${member.email} da equipe?`, message: "O acesso à loja termina na hora. Você pode convidar de novo depois.", confirmLabel: "Remover da equipe", tone: "danger" }))) return;
    void mutate(() => apiRequest(`${api}/${encodeURIComponent(member.userId)}`, { method: "DELETE" }), `${member.email} não tem mais acesso à loja.`);
  }

  async function cancel(invitation: StaffOverview["invitations"][number]) {
    if (!(await confirm({ title: `Cancelar o convite de ${invitation.email}?`, message: "O link enviado deixa de funcionar.", confirmLabel: "Cancelar convite", cancelLabel: "Manter convite", tone: "danger" }))) return;
    void mutate(() => apiRequest(`${api}/invitations/${encodeURIComponent(invitation.id)}`, { method: "DELETE" }), "Convite cancelado.");
  }

  const limit = overview ? staffLimitText(overview) : null;
  const feedbackNode = feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null;
  return <section className="page store-categories" aria-labelledby="staff-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="staff-title" title="Funcionários"
      description="Convide quem vai operar o caixa e registrar vendas presenciais. Funcionários não veem relatórios, custos, margens nem contas a pagar."
      action={state === "ready" ? <AddButton onClick={() => { setFeedback(null); setInviting(true); }}>Convidar funcionário</AddButton> : undefined} />
    {state === "loading" ? <Loading context="a equipe" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para gerenciar a equipe.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Só o dono da loja gerencia a equipe.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar a equipe" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {inviting ? null : feedbackNode}
    {state === "ready" && overview ? <>
      {limit ? <p className="field-help">{limit} <Link href="/conta/plano">Plano e cobrança</Link></p> : null}
      <h2>Equipe</h2>
      {overview.members.length === 0 ? <p>Ninguém na equipe ainda.</p> : <ul className="category-list">
        {overview.members.map(member => <li key={member.userId} className="category-item">
          <span className="category-name">{member.email}<span className="cell-hint">Na equipe desde {dateText(member.since)}</span></span>
          <span className="form-actions"><button type="button" className="button-secondary" disabled={busy} aria-label={`Remover ${member.email} da equipe`} onClick={() => void revoke(member)}>Remover</button></span>
        </li>)}
      </ul>}
      {overview.invitations.length ? <>
        <h2>Convites em aberto</h2>
        <ul className="category-list">
          {overview.invitations.map(invitation => <li key={invitation.id} className="category-item">
            <span className="category-name">{invitation.email}<span className="cell-hint">{invitation.expired ? "Expirado; envie de novo" : `Vale até ${dateText(invitation.expiresAt)}`}</span></span>
            <span className="form-actions"><button type="button" className="button-secondary" disabled={busy} aria-label={`Cancelar o convite de ${invitation.email}`} onClick={() => void cancel(invitation)}>Cancelar convite</button></span>
          </li>)}
        </ul>
      </> : null}
      <FormSheet open={inviting} onOpenChange={setInviting} titleId="staff-invite-title" title="Convidar funcionário" busy={busy} feedbackRef={feedbackRef}>{feedbackNode}
      <form onSubmit={event => void invite(event)} noValidate aria-labelledby="staff-invite-title">
        <div className="form-field">
          <label htmlFor="staff-email">E-mail</label>
          <p id="staff-email-help" className="field-help">A pessoa recebe um link e aceita entrando com este e-mail. O convite vale por 7 dias.</p>
          <input ref={emailRef} id="staff-email" type="email" value={email} maxLength={320} autoComplete="off" disabled={busy}
            onChange={event => { setEmail(event.target.value); setEmailError(null); }}
            aria-invalid={!!emailError} aria-describedby={`staff-email-help${emailError ? " staff-email-error" : ""}`} />
          {emailError ? <p id="staff-email-error" className="field-error">{emailError}</p> : null}
        </div>
        <button type="submit" disabled={busy}>{busy ? "Enviando…" : "Enviar convite"}</button>
      </form>
      </FormSheet>
    </> : null}
  </section>;
}
