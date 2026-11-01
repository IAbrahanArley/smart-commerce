"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { CreditCard, ExternalLink } from "lucide-react";
import { apiRequest, requestError } from "@/lib/auth-api";
import {
  backendCycle, backendPlan, billingRefusal, canSubscribe, checkoutMessage, checkoutTarget, CYCLE_NAME, cycleId, dateText, moneyText,
  parseAccount, parsePreview, parseSubscription, PLAN_NAME, planId, portalTarget, type BillingAccount, type ChangePreview, type SubscriptionState,
} from "@/lib/billing";
import { cycleTotalText, planById, type Cycle, type PlanId } from "@/lib/plans";
import { useConfirm } from "@/components/confirm-dialog";
import BillingPlans from "@/components/billing-plans";
import BillingSummary from "@/components/billing-summary";
import PageHeader from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Loading } from "@/components/ui/loading";

const CONFIRM_ATTEMPTS = 10;
const CONFIRM_INTERVAL_MS = 3000;
type Feedback = { text: string; error: boolean } | null;

// SC-033 and ADR-0017 B13: the owner's plan, its changes and cancellation. Card data is typed only on
// Stripe's pages (Checkout and the portal for card and receipts). Every change is previewed by the
// backend, which reads Stripe; the panel never computes an amount and only shows what it is told.
export default function BillingPanel({ result }: { result: string | null }) {
  const [account, setAccount] = useState<BillingAccount | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionState | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [cycle, setCycle] = useState<Cycle>("monthly");
  const [busy, setBusy] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(() => checkoutMessage(result));
  const [confirming, setConfirming] = useState(result === "sucesso");
  const confirmingRef = useRef(result === "sucesso");
  const cycleChosen = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const { confirm, dialog } = useConfirm();
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
      // The running subscription (pending change, cancellation) only exists once the owner subscribed.
      let running: SubscriptionState | null = null;
      if (current.enforced && !canSubscribe(current)) {
        const live = await apiRequest("/api/billing/subscription", { signal: controller.signal }).catch(() => null);
        running = live?.ok ? parseSubscription(await live.json()) : null;
      }
      if (controller.signal.aborted) return;
      setAccount(current); setSubscription(running); setState("ready");
      if (!cycleChosen.current && current.cycle) { cycleChosen.current = true; setCycle(cycleId(current.cycle)); }
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

  /** Sends one billing request; returns its JSON body, or null after showing why it failed. */
  async function send(key: string, path: string, method: "POST" | "DELETE", body?: object): Promise<unknown> {
    setBusy(key); setFeedback(null);
    try {
      const response = await apiRequest(path, { method, body });
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente para continuar.", error: true }); return null; }
      const json: unknown = await response.json().catch(() => null);
      if (!response.ok) {
        setFeedback({ text: billingRefusal(response.status, json) ?? requestError(response.status), error: true });
        if (response.status === 402 || response.status === 409) setAttempt(current => current + 1);
        return null;
      }
      return json;
    } catch {
      setFeedback({ text: "Não foi possível falar com o servidor. Tente novamente.", error: true });
      return null;
    } finally { setBusy(null); }
  }

  async function leaveFor(key: string, path: string, body: object | undefined, target: (raw: unknown) => string | null) {
    if (busy) return;
    const json = await send(key, path, "POST", body);
    if (json === null) return;
    const url = target(json);
    if (!url) { setFeedback({ text: "Não foi possível abrir a página da Stripe. Tente novamente.", error: true }); return; }
    // Keep the buttons disabled while the browser leaves for Stripe (no second session).
    setBusy(key);
    window.location.assign(url);
  }

  function applied(json: unknown, text: string) {
    const next = parseSubscription(json);
    if (next) setSubscription(next);
    setFeedback({ text, error: false });
    setAttempt(current => current + 1);
  }

  async function changeTo(plan: PlanId) {
    if (busy) return;
    const body = { plan: backendPlan(plan), cycle: backendCycle(cycle) };
    const json = await send(plan, "/api/billing/change/preview", "POST", body);
    if (json === null) return;
    const preview = parsePreview(json);
    if (!preview) { setFeedback({ text: "Não foi possível conferir a mudança. Tente novamente.", error: true }); return; }
    const now = preview.timing === "NOW";
    const money = now && preview.amountDue !== null && preview.currency ? moneyText(preview.amountDue, preview.currency) : null;
    const confirmed = await confirm({
      title: `Mudar para ${PLAN_NAME[preview.plan]} ${CYCLE_NAME[preview.cycle].toLowerCase()}?`,
      message: <ChangeMessage preview={preview} account={account} money={money} />,
      confirmLabel: !now ? "Agendar mudança" : preview.amountDue ? `Pagar ${money} e mudar` : "Confirmar mudança",
    });
    if (!confirmed) return;
    // The preview's date and amount go back unchanged: the backend charges only if Stripe still agrees.
    const done = await send(plan, "/api/billing/change", "POST", { ...body, prorationDate: preview.prorationDate, amountDue: preview.amountDue });
    if (done === null) return;
    applied(done, now
      ? `Pronto: seu plano agora é ${PLAN_NAME[preview.plan]} ${CYCLE_NAME[preview.cycle].toLowerCase()}. ${money} foi cobrado no cartão da assinatura.`
      : `Mudança agendada: em ${dateText(preview.effectiveAt)}, seu plano passa para ${PLAN_NAME[preview.plan]} ${CYCLE_NAME[preview.cycle].toLowerCase()}.`);
  }

  async function cancelSubscription() {
    if (busy) return;
    const end = subscription?.currentPeriodEnd ?? account?.currentPeriodEnd ?? null;
    const confirmed = await confirm({
      title: "Cancelar a assinatura?", tone: "danger", confirmLabel: "Cancelar assinatura", cancelLabel: "Manter assinatura",
      message: <>
        <p>Nada é cobrado de novo. Sua assinatura continua funcionando {end ? <>até <strong>{dateText(end)}</strong></> : "até o fim do período pago"}; você pode mantê-la até lá.</p>
        <p>Depois, o painel fica só para consulta e a vitrine sai do ar. Nenhum dado é apagado.</p>
        {/* No link here: the first focusable control of the dialog must be "Manter assinatura". */}
        <p>Contratou ou renovou há até 7 dias? Você tem direito ao reembolso integral, conforme os Termos de uso.</p>
      </>,
    });
    if (!confirmed) return;
    const json = await send("cancel", "/api/billing/cancel", "POST");
    if (json !== null) applied(json, `Cancelamento agendado${end ? ` para ${dateText(end)}` : ""}. Até lá, tudo continua funcionando.`);
  }

  async function resume() {
    const json = await send("resume", "/api/billing/resume", "POST");
    if (json !== null) applied(json, "Assinatura mantida. Ela renova normalmente.");
  }

  async function undo() {
    const json = await send("undo", "/api/billing/change", "DELETE");
    if (json !== null) applied(json, "Mudança desfeita. Seu plano atual renova normalmente.");
  }

  const subscribing = account ? canSubscribe(account) : false;
  const current = account?.plan && account.cycle ? { plan: planId(account.plan), cycle: cycleId(account.cycle) } : null;
  const pending = subscription?.pendingChange ? { plan: planId(subscription.pendingChange.plan), cycle: cycleId(subscription.pendingChange.cycle) } : null;
  const blocked = !account || subscribing ? null
    : account.status !== "ACTIVE" ? "Regularize o pagamento em “Cartão e recibos” para voltar a mudar de plano."
    : subscription?.cancelAtPeriodEnd ? "Sua assinatura está marcada para encerrar. Mantenha a assinatura para mudar de plano."
    : subscription?.pendingChange ? "Você já tem uma mudança agendada. Desfaça essa mudança para escolher outra."
    : !subscription ? "Não foi possível consultar sua assinatura agora. Recarregue a página para mudar de plano." : null;

  return <section className="page page-panel" aria-labelledby="billing-title" aria-busy={state === "loading" || busy !== null || confirming}>
    <PageHeader id="billing-title" title="Plano e cobrança" breadcrumbs={[{ href: "/conta", label: "Minha conta" }]}
      description="Acompanhe seu plano, mude de plano ou de ciclo e cancele quando quiser. O cartão fica na página segura da Stripe; a plataforma não vê nem guarda dados de cartão." />
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {confirming ? <Loading context="a confirmação da assinatura" /> : null}
    {state === "loading" ? <Loading context="seu plano" /> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar seu plano. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar seu plano.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(next => next + 1); }}>Consultar novamente</button></> : null}
    {state === "ready" && account ? <div className="flex flex-col gap-8">
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2"><BillingSummary account={account} subscription={subscription} busy={busy} onUndo={() => void undo()} onResume={() => void resume()} /></div>
        {account.enforced && !subscribing ? <Card>
          <CardHeader><CardTitle>Pagamento</CardTitle><CardDescription>Cartão de crédito e recibos ficam na página segura da Stripe.</CardDescription></CardHeader>
          <CardContent className="flex flex-col gap-3">
            <p className="m-0 flex items-center gap-2 text-sm text-muted-foreground"><CreditCard className="size-4 shrink-0" aria-hidden="true" />Troque o cartão ou baixe recibos e notas.</p>
            <Button type="button" variant="outline" disabled={busy !== null} onClick={() => void leaveFor("portal", "/api/billing/portal", undefined, portalTarget)}>
              {busy === "portal" ? "Abrindo a Stripe…" : <>Cartão e recibos <ExternalLink aria-hidden="true" /></>}
            </Button>
          </CardContent>
        </Card> : null}
      </div>
      {!account.enforced ? <p className="field-help">A cobrança ainda não está ligada neste ambiente: nenhum limite é aplicado e não há como assinar.</p> : <>
        <BillingPlans mode={subscribing ? "subscribe" : "change"} cycle={cycle} onCycle={setCycle} current={subscribing ? null : current} pending={pending}
          busy={busy} blocked={blocked}
          onChoose={plan => void (subscribing ? leaveFor(plan, "/api/billing/checkout", { plan: backendPlan(plan), cycle: backendCycle(cycle) }, checkoutTarget) : changeTo(plan))} />
        {subscribing ? <p className="field-help">Você pode desistir em até 7 dias da contratação ou da renovação semestral ou anual, com reembolso integral; veja os <Link href="/termos">Termos de uso</Link>.</p> : null}
        {!subscribing && subscription && !subscription.cancelAtPeriodEnd ? <section aria-labelledby="cancel-title" className="flex flex-col gap-3 rounded-lg border border-border p-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 id="cancel-title" className="m-0 text-base font-semibold">Cancelar assinatura</h2>
            <p className="m-0 mt-1 text-sm text-muted-foreground">O cancelamento vale no fim do período pago. Nenhum dado é apagado. Desistência em até 7 dias da contratação ou renovação tem reembolso integral; veja os <Link href="/termos">Termos de uso</Link>.</p>
          </div>
          {subscription.pendingChange ? <p className="m-0 text-sm text-muted-foreground">Desfaça a mudança agendada para poder cancelar.</p>
            : <Button type="button" variant="outline" className="text-destructive" disabled={busy !== null} onClick={() => void cancelSubscription()}>{busy === "cancel" ? "Cancelando…" : "Cancelar assinatura"}</Button>}
        </section> : null}
      </>}
    </div> : null}
    {dialog}
  </section>;
}

function ChangeMessage({ preview, account, money }: { preview: ChangePreview; account: BillingAccount | null; money: string | null }) {
  const target = cycleTotalText(planById(planId(preview.plan)), cycleId(preview.cycle));
  const currentName = account?.plan ? PLAN_NAME[account.plan] : "atual";
  return <>
    {preview.timing === "NOW" ? <>
      <p>Cobrança agora: <strong>{money}</strong> no cartão da assinatura. É a diferença proporcional ao tempo que falta no período, já descontado o que você pagou.</p>
      <p>O novo plano vale na hora. Nas próximas renovações: {target}.</p>
    </> : <>
      <p>Nada é cobrado agora. Você continua no plano {currentName} até <strong>{dateText(preview.effectiveAt)}</strong>.</p>
      <p>A partir daí, passa para {PLAN_NAME[preview.plan]} {CYCLE_NAME[preview.cycle].toLowerCase()}: {target}. Você pode desfazer até lá.</p>
    </>}
    {preview.exceedsCompanies ? <p><strong>Atenção:</strong> você tem mais lojas do que esse plano permite. Nada é apagado, mas não será possível criar lojas novas.</p> : null}
  </>;
}
