"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { type FormEvent, useEffect, useEffectEvent, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import {
  type AdvisorStatus, type Exchange, MAX_EXCHANGES, MAX_QUESTION, SUGGESTIONS,
  askError, historyFor, outcomeText, parseAnswer, parseStatus, questionError, quotaText, sourceText,
} from "@/lib/advisor";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";

// SC-027 (ADR-0016 A5, A7, A12): one short conversation per visit, kept only in this page. Every
// answer shows its sources; the backend has already refused any answer with a number that has no
// source. A polite status region announces progress, and focus moves to each new answer.
export default function StoreAdvisor({ tenantId, initialQuestion = null }: { tenantId: string; initialQuestion?: string | null }) {
  const [status, setStatus] = useState<AdvisorStatus | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const [question, setQuestion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const submitting = useRef(false);
  const nextId = useRef(1);
  const focusInput = useRef(false);
  const explained = useRef(false);
  const router = useRouter();
  const pathname = usePathname();
  const latestRef = useRef<HTMLElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/advisor`;
  const full = exchanges.length >= MAX_EXCHANGES;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const parsed = parseStatus(await response.json());
      if (!parsed) throw new Error();
      if (controller.signal.aborted) return;
      setStatus(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => {
    // A new conversation may come from the "full" state, where the field is not mounted yet.
    if (focusInput.current) { focusInput.current = false; inputRef.current?.focus(); }
    else if (exchanges.length) latestRef.current?.focus();
  }, [exchanges.length]);

  async function ask(text: string) {
    if (submitting.current || full) return;
    const invalid = questionError(text);
    if (invalid) { setError(invalid); inputRef.current?.focus(); return; }
    submitting.current = true; setBusy(true); setError(null); setAnnouncement("Consultando os dados da loja…");
    try {
      const response = await apiRequest(`${api}/questions`, { method: "POST", body: { question: text.trim(), history: historyFor(exchanges) } });
      const result = response.ok ? parseAnswer(await response.json()) : null;
      if (!response.ok || !result) {
        const message = response.ok ? askError(500) : askError(response.status);
        setError(message); setAnnouncement(message); inputRef.current?.focus();
        if (response.status === 429 && status) setStatus({ ...status, remaining: 0 });
        return;
      }
      const id = nextId.current++;
      setExchanges(current => [...current, { id, question: text.trim(), result }]);
      setQuestion("");
      if (status) setStatus({ ...status, usedThisMonth: status.usedThisMonth + 1, remaining: Math.max(0, status.remaining - 1) });
      setAnnouncement(result.outcome === "ANSWERED" ? "Resposta recebida." : "O assistente não respondeu a esta pergunta.");
    } catch {
      const message = askError(0);
      setError(message); setAnnouncement(message); inputRef.current?.focus();
    } finally { submitting.current = false; setBusy(false); }
  }

  // SC-028: "Explicar" asks its fixed question once, then drops it from the URL so a reload does
  // not spend another question of the quota.
  const explain = useEffectEvent((text: string) => {
    explained.current = true;
    router.replace(pathname, { scroll: false });
    setQuestion(text);
    void ask(text);
  });
  const canAsk = state === "ready" && Boolean(status?.enabled) && (status?.remaining ?? 0) > 0;
  useEffect(() => {
    if (initialQuestion && canAsk && !explained.current) explain(initialQuestion);
  }, [initialQuestion, canAsk]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void ask(question);
  }

  function restart() {
    focusInput.current = true;
    setExchanges([]); setQuestion(""); setError(null); setAnnouncement("Nova conversa iniciada.");
  }

  const length = [...question].length;
  const outOfQuestions = status ? status.remaining === 0 : false;

  return <section className="page advisor" aria-labelledby="advisor-title">
    <PageHeader id="advisor-title" title="Assistente"
      description="Pergunte sobre vendas, pedidos, estoque, finanças e alertas da loja. O assistente só lê dados: não altera preços, estoque nem pedidos." />
    <p className="visually-hidden" role="status" aria-live="polite">{announcement}</p>
    {state === "loading" ? <PageState kind="loading" icon="spark" title="Consultando o assistente…">Verificando se o assistente está disponível para esta loja.</PageState> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para usar o assistente.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="spark" title="Não foi possível consultar o assistente" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Tentar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {state === "ready" && status && !status.enabled ? <PageState icon="spark" title="Assistente indisponível">O assistente não está ligado neste ambiente. Os alertas e os relatórios da loja continuam funcionando normalmente.</PageState> : null}
    {state === "ready" && status?.enabled ? <>
      <p className="notice advisor-notice"><strong>Respostas geradas por IA.</strong> Elas usam só os dados da loja e mostram as fontes de cada número, mas podem conter erros: confira nas telas indicadas antes de decidir.</p>

      {exchanges.length ? <ol className="advisor-conversation" aria-label="Conversa com o assistente">
        {exchanges.map((exchange, index) => {
          const latest = index === exchanges.length - 1;
          const notice = outcomeText(exchange.result.outcome);
          return <li key={exchange.id} className="advisor-exchange">
            <p className="advisor-question"><span className="visually-hidden">Você perguntou: </span>{exchange.question}</p>
            <article className={`advisor-answer${notice ? " advisor-answer-empty" : ""}`} ref={latest ? latestRef : undefined} tabIndex={latest ? -1 : undefined}
              aria-labelledby={`advisor-answer-${exchange.id}`}>
              <h2 id={`advisor-answer-${exchange.id}`} className="advisor-answer-title">Resposta {index + 1}</h2>
              {notice ? <p>{notice}</p> : <p className="advisor-answer-text">{exchange.result.answer}</p>}
              {exchange.result.sources.length ? <div className="advisor-sources">
                <h3>Fontes</h3>
                <ul>{exchange.result.sources.map(source => <li key={source.tool}>{sourceText(source)}</li>)}</ul>
              </div> : null}
            </article>
          </li>;
        })}
      </ol> : <div className="side-card advisor-suggestions">
        <h2>Sugestões</h2>
        <p>Escolha uma pergunta pronta ou escreva a sua.</p>
        <ul className="advisor-suggestion-list">
          {SUGGESTIONS.map(suggestion => <li key={suggestion}>
            <button type="button" className="button-secondary" disabled={busy || outOfQuestions} onClick={() => { setQuestion(suggestion); void ask(suggestion); }}>{suggestion}</button>
          </li>)}
        </ul>
      </div>}

      {full ? <div className="side-card">
        <h2>Conversa completa</h2>
        <p>Esta conversa chegou a {MAX_EXCHANGES} perguntas. Para continuar, comece uma nova; a anterior não fica salva.</p>
        <button type="button" onClick={restart}>Nova conversa</button>
      </div> : <form className="side-card advisor-form" onSubmit={submit} noValidate aria-labelledby="advisor-form-title" aria-busy={busy}>
        <h2 id="advisor-form-title">{exchanges.length ? "Continuar a conversa" : "Sua pergunta"}</h2>
        <div className="form-field">
          <label htmlFor="advisor-question">Pergunta</label>
          <p id="advisor-question-help" className="field-help">Até {MAX_QUESTION} caracteres. {quotaText(status)}</p>
          <textarea ref={inputRef} id="advisor-question" name="question" rows={3} maxLength={MAX_QUESTION * 2} autoComplete="off" value={question}
            disabled={busy || outOfQuestions} aria-invalid={error ? true : undefined}
            aria-describedby={`advisor-question-help advisor-question-count${error ? " advisor-question-error" : ""}`}
            onChange={event => { setQuestion(event.target.value); setError(null); }}
            onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void ask(question); } }} />
          <p id="advisor-question-count" className={`field-help${length > MAX_QUESTION ? " field-error" : ""}`}>{length} de {MAX_QUESTION} caracteres</p>
          {error ? <p id="advisor-question-error" className="field-error" role="alert">{error}</p> : null}
        </div>
        <div className="form-actions">
          <button type="submit" disabled={busy || outOfQuestions}>{busy ? "Consultando…" : "Perguntar"}</button>
          {exchanges.length ? <button type="button" className="button-secondary" disabled={busy} onClick={restart}>Nova conversa</button> : null}
        </div>
        {busy ? <p className="field-help">O assistente está consultando os dados da loja. Isso pode levar alguns segundos.</p> : null}
        {outOfQuestions ? <p className="field-help">A loja já usou todas as perguntas deste mês.</p> : null}
      </form>}
    </> : null}
  </section>;
}
