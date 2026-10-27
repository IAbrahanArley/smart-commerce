"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import {
  backendCycle, backendPlan, billingNotice, billingRefusal, canSubscribe, checkoutMessage, checkoutTarget, CYCLE_NAME, dateText,
  parseAccount, PLAN_NAME, portalTarget, STATUS_TEXT, trialDaysLeft, type BillingAccount,
} from "@/lib/billing";
import { billingText, CYCLES, monthlyPrice, PLANS, priceText, type Cycle, type PlanId } from "@/lib/plans";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { Loading } from "@/components/ui/loading";

const CONFIRM_ATTEMPTS = 10;
const CONFIRM_INTERVAL_MS = 3000;

// SC-033 (ADR-0017 B3, B8): the owner's plan, limits and the way to Stripe. Card data is typed only
// on Stripe's pages; the account changes only when the backend receives Stripe's signed webhook, so
// the return from Checkout just waits for that confirmation.
export default function BillingPanel({ result }: { result: string | null }) {
  const [account, setAccount] = useState<BillingAccount | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [cycle, setCycle] = useState<Cycle>("monthly");
  const [busy, setBusy] = useState<PlanId | "portal" | null>(null);
  const [feedback, setFeedback] = useState(() => checkoutMessage(result));
  const [confirming, setConfirming] = useState(result === "sucesso");
  const confirmingRef = useRef(result === "sucesso");
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const router = useRouter();
  const pathname = usePathname();

  // The result word is shown once; reloading the page must not repeat it.
  useEffect(() => {
    if (!result) return;
    const timer = window.setTimeout(() => router.replace(pathname, { scroll: false }), 0);
    return () => window.clearTimeout(timer);
  }, [result, router, pathname]);

  useEffect(() => {
    const controller = new AbortController();
    let timer: number | undefined;
    apiRequest("/api/billing/account", { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (!response.ok) throw new Error();
      const current = parseAccount(await response.json());
      if (!current) throw new Error();
      setAccount(current); setState("ready");
      // After Checkout, ask again a few times until the webhook has activated the subscription.
      if (!confirmingRef.current) return;
      if (current.status === "ACTIVE") {
        confirmingRef.current = false; setConfirming(false);
        setFeedback({ text: `Assinatura confirmada: plano ${current.plan ? PLAN_NAME[current.plan] : ""}.`, error: false });
      } else if (attempt >= CONFIRM_ATTEMPTS) {
        confirmingRef.current = false; setConfirming(false);
        setFeedback({ text: "A confirmação está demorando. Se o pagamento foi aprovado, a assinatura aparece aqui em alguns minutos; a Stripe também envia o recibo por e-mail.", error: false });
      } else timer = window.setTimeout(() => setAttempt(next => next + 1), CONFIRM_INTERVAL_MS);
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [attempt]);

  useEffect(() => {
    if (!feedback) return;
    const frame = requestAnimationFrame(() => feedbackRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [feedback]);

  async function leaveFor(path: string, body: object | undefined, target: (raw: unknown) => string | null, key: PlanId | "portal") {
    let navigating = false;
    if (busy) return;
    setBusy(key); setFeedback(null);
    try {
      const response = await apiRequest(path, { method: "POST", body });
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente para continuar.", error: true }); return; }
      if (!response.ok) {
        const refusal = billingRefusal(response.status, await response.json().catch(() => null));
        setFeedback({ text: refusal ?? requestError(response.status), error: true });
        if (refusal) setAttempt(current => current + 1);
        return;
      }
      const url = target(await response.json());
      if (!url) { setFeedback({ text: "Não foi possível abrir a página da Stripe. Tente novamente.", error: true }); return; }
      // Keep the buttons disabled while the browser leaves for Stripe (no second session).
      navigating = true;
      window.location.assign(url);
    } catch { setFeedback({ text: "Não foi possível abrir a página da Stripe. Tente novamente.", error: true }); }
    finally { if (!navigating) setBusy(null); }
  }

  const notice = account ? billingNotice(account, new Date()) : null;
  const subscribe = (plan: PlanId) => leaveFor("/api/billing/checkout", { plan: backendPlan(plan), cycle: backendCycle(cycle) }, checkoutTarget, plan);
  const manage = () => leaveFor("/api/billing/portal", undefined, portalTarget, "portal");

  return <section className="page page-panel" aria-labelledby="billing-title" aria-busy={state === "loading" || busy !== null || confirming}>
    <PageHeader id="billing-title" title="Plano e cobrança" breadcrumbs={[{ href: "/conta", label: "Minha conta" }]}
      description="Seu plano vale para todas as suas lojas. O pagamento é feito na página segura da Stripe; a plataforma não vê nem guarda dados de cartão." />
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {confirming ? <Loading context="a confirmação da assinatura" /> : null}
    {state === "loading" ? <Loading context="seu plano" /> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar seu plano. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar seu plano.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar novamente</button></> : null}
    {state === "ready" && account ? <>
      <div className="variant-definition">
        <h2>Situação</h2>
        <p><StatusBadge tone={account.status === "ACTIVE" || account.status === "TRIAL" ? "positive" : "neutral"}>{STATUS_TEXT[account.status]}</StatusBadge></p>
        <dl className="order-facts">
          <div><dt>Plano</dt><dd>{account.plan ? PLAN_NAME[account.plan] : "Profissional (liberado no teste)"}</dd></div>
          {account.cycle ? <div><dt>Ciclo</dt><dd>{CYCLE_NAME[account.cycle]}</dd></div> : null}
          {account.status === "TRIAL" && account.trialEndsAt ? <div><dt>Teste termina em</dt><dd>{dateText(account.trialEndsAt)} ({trialDaysLeft(account, new Date())} {trialDaysLeft(account, new Date()) === 1 ? "dia" : "dias"})</dd></div> : null}
          {account.currentPeriodEnd && account.status !== "TRIAL" ? <div><dt>{account.status === "ACTIVE" ? "Próxima cobrança" : account.status === "CANCELED" ? "Período pago até" : "Período atual até"}</dt><dd>{dateText(account.currentPeriodEnd)}</dd></div> : null}
          <div><dt>Lojas</dt><dd>{account.companies} de {account.limits.companies}</dd></div>
          <div><dt>Produtos por loja</dt><dd>até {account.limits.productsPerStore.toLocaleString("pt-BR")}</dd></div>
          <div><dt>Perguntas ao assistente</dt><dd>{account.limits.monthlyQuestions === 0 ? "não incluídas" : `${account.limits.monthlyQuestions} por mês`}</dd></div>
        </dl>
        {notice ? <p className="field-help">{notice.text}</p> : null}
        {!account.enforced ? <p className="field-help">A cobrança ainda não está ligada neste ambiente: nenhum limite é aplicado e não há como assinar.</p> : null}
        {account.enforced && !canSubscribe(account) ? <div className="publication-actions billing-actions">
          <button type="button" disabled={busy !== null} onClick={() => void manage()}>{busy === "portal" ? "Abrindo a Stripe…" : "Gerenciar assinatura"}</button>
          <p className="field-help">Troque de plano ou de ciclo, atualize o cartão, veja os recibos ou cancele. O cancelamento vale no fim do período pago.</p>
        </div> : null}
      </div>
      {account.enforced && canSubscribe(account) ? <section aria-labelledby="choose-plan">
        <h2 id="choose-plan">Escolha seu plano</h2>
        <fieldset className="billing-cycles">
          <legend>Ciclo de cobrança</legend>
          {CYCLES.map(option => <label key={option.id}>
            <input type="radio" name="cycle" value={option.id} checked={cycle === option.id} onChange={() => setCycle(option.id)} disabled={busy !== null} />
            {option.label}{option.discount ? ` (−${option.discount}%)` : ""}
          </label>)}
        </fieldset>
        <ul className="billing-plans">
          {PLANS.map(plan => <li key={plan.id} className="variant-definition">
            <h3>{plan.name}</h3>
            <p className="field-help">{plan.summary}</p>
            <p><strong className="billing-price">{priceText(monthlyPrice(plan, cycle))}</strong> por mês</p>
            <p className="field-help">{billingText(plan, cycle)}</p>
            <ul>{plan.features.map(feature => <li key={feature}>{feature}</li>)}</ul>
            <button type="button" disabled={busy !== null} onClick={() => void subscribe(plan.id)}>{busy === plan.id ? "Abrindo a Stripe…" : `Assinar ${plan.name}`}</button>
          </li>)}
        </ul>
        <p className="field-help">Pagamento com cartão de crédito, renovado automaticamente a cada ciclo. Você pode desistir em até 7 dias da contratação ou da renovação semestral ou anual, com reembolso integral; veja os <Link href="/termos">Termos de uso</Link>.</p>
      </section> : null}
    </> : null}
  </section>;
}
