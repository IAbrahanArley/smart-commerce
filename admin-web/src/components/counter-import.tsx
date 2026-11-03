"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type Account, parseAccounts } from "@/lib/accounts";
import { METHOD_LABELS } from "@/lib/counter-sales";
import { type ImportPreview, type ImportResult, STATUS_LABELS, TEMPLATE, fileError, linesText } from "@/lib/counter-import";
import { formatDay } from "@/lib/expenses";
import StatusBadge from "@/components/status-badge";

// SC-054 (ADR-0019 C14): the OWNER imports counter sales from a CSV spreadsheet. The file is sent
// for a preview (nothing written), then confirmed: only the sales marked ready are written, and
// sending the same or a corrected file never writes a sale twice.
export default function CounterImport({ tenantId, onImported }: { tenantId: string; onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [cashAccount, setCashAccount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const summaryRef = useRef<HTMLHeadingElement>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/counter-sales/imports`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/finance/accounts`, { signal: controller.signal }).then(async response => {
      if (!response.ok) return;
      const parsed = parseAccounts(await response.json());
      if (parsed && !controller.signal.aborted) setAccounts(parsed.filter(account => account.type === "CASH" || account.type === "BANK"));
    }).catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);
  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  // After an import the feedback keeps the focus; a plain preview moves it to the summary.
  useEffect(() => { if (preview && !feedback) summaryRef.current?.focus(); }, [preview, feedback]);

  function download() {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url; link.download = "modelo-vendas-na-loja.csv"; link.click();
    // Revoked later: some browsers cancel a download whose URL is revoked at once.
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function send(confirm: boolean, keepFeedback = false) {
    const problem = fileError(file);
    if (problem) { setError(problem); document.getElementById("import-file")?.focus(); return; }
    if (confirm && preview?.needsCashAccount && !cashAccount) { setAccountError("Escolha a gaveta ou a conta bancária que recebeu o dinheiro das vendas em dinheiro."); document.getElementById("import-cash-account")?.focus(); return; }
    setBusy(true); if (!keepFeedback) setFeedback(null); setError(null);
    const form = new FormData();
    form.append("file", file!);
    if (confirm && cashAccount) form.append("cashAccountId", cashAccount);
    try {
      const response = await apiRequest(confirm ? api : `${api}/preview`, { method: "POST", body: form });
      if (response.ok) {
        if (confirm) {
          const result: ImportResult = await response.json();
          // The confirmation checks the stock again: some sales may have been left out. A fresh
          // preview of the same file shows what was imported and what still waits, and why.
          const left = (preview?.ready ?? 0) - result.imported;
          setCashAccount("");
          setFeedback({ text: `${result.imported ? `${result.imported} ${result.imported === 1 ? "venda importada" : "vendas importadas"}. O estoque e o financeiro foram atualizados.` : "Nenhuma venda nova para importar."}${left > 0 ? ` ${left} ${left === 1 ? "venda ficou" : "vendas ficaram"} de fora porque o estoque mudou desde a prévia: veja abaixo.` : ""}`, error: left > 0 });
          if (result.imported) onImported();
          setBusy(false);
          await send(false, true);
          return;
        } else { setPreview(await response.json()); }
        return;
      }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de importar.", error: true }); return; }
      if (response.status === 409) {
        const answer = await response.json().catch(() => null) as { message?: string } | null;
        setFeedback({ text: answer?.message ?? "A importação não pôde ser feita. Confira a prévia de novo.", error: true }); return;
      }
      if (response.status === 400) { setFeedback({ text: "O arquivo não pôde ser lido. Confira se é um CSV com o cabeçalho do modelo.", error: true }); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível enviar o arquivo. Tente de novo: a mesma planilha nunca importa uma venda duas vezes.", error: true });
    } finally { setBusy(false); }
  }

  return <section className="side-card" aria-labelledby="import-title">
    <h2 id="import-title">Importar vendas de uma planilha</h2>
    <p className="field-help">Uma linha por item. Linhas com a mesma data e o mesmo código de venda formam uma venda; sem código, cada linha é uma venda. Uma venda já importada não se repete, nem corrigida: para mudar uma, cancele-a e registre de novo. Formas de pagamento: dinheiro, pix, débito ou crédito (crediário não é aceito). Até 500 linhas e 1 MB.</p>
    <div className="form-actions"><button type="button" className="button-secondary" onClick={download}>Baixar modelo</button></div>
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null}
    <form onSubmit={(event: FormEvent<HTMLFormElement>) => { event.preventDefault(); void send(false); }} noValidate>
      <div className="form-field">
        <label htmlFor="import-file">Planilha (CSV)</label>
        <p id="import-file-help" className="field-help">No Excel: Arquivo → Salvar como → CSV. A prévia não grava nada.</p>
        <input id="import-file" type="file" accept=".csv,text/csv" disabled={busy}
          onChange={event => { setFile(event.target.files?.[0] ?? null); setPreview(null); setError(null); setFeedback(null); }}
          aria-invalid={error ? true : undefined} aria-describedby={["import-file-help", error ? "import-file-error" : null].filter(Boolean).join(" ")} />
        {error ? <p id="import-file-error" className="field-error">{error}</p> : null}
      </div>
      <div className="form-actions"><button type="submit" disabled={busy}>{busy && !preview ? "Lendo…" : "Ver prévia"}</button></div>
    </form>
    {preview ? <>
      <h3 ref={summaryRef} tabIndex={-1}>Prévia: {preview.ready} {preview.ready === 1 ? "venda pronta" : "vendas prontas"} ({formatBRL(preview.readyTotal)}), {preview.alreadyImported} já {preview.alreadyImported === 1 ? "importada" : "importadas"}, {preview.withErrors} com erro</h3>
      {preview.problems.length ? <ul className="field-error">{preview.problems.map(problem => <li key={`${problem.line}-${problem.message}`}>{problem.line ? `Linha ${problem.line}: ` : ""}{problem.message}</li>)}</ul> : null}
      {preview.sales.length ? <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
        <caption>Vendas lidas da planilha</caption>
        <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Venda</th><th role="columnheader" scope="col">Data e pagamento</th><th role="columnheader" scope="col" className="numeric">Total</th><th role="columnheader" scope="col">Situação</th></tr></thead>
        <tbody role="rowgroup">{preview.sales.map(sale => <tr role="row" key={sale.lines.join("-")}>
          <th role="rowheader" scope="row">{sale.items.map(item => `${item.quantity}× ${item.name ?? item.sku}`).join(", ")}<span className="cell-hint">{linesText(sale.lines)}{sale.code ? ` · venda ${sale.code}` : ""}</span></th>
          <td role="cell" data-label="Data e pagamento">{formatDay(sale.date)}<span className="cell-hint">{METHOD_LABELS[sale.paymentMethod]}</span></td>
          <td role="cell" data-label="Total" className="numeric">{formatBRL(sale.total)}</td>
          <td role="cell" data-label="Situação"><StatusBadge tone={sale.status === "READY" ? "positive" : sale.status === "ERROR" ? "danger" : "neutral"}>{STATUS_LABELS[sale.status]}</StatusBadge>
            {sale.messages.map(message => <span key={message} className="cell-hint">{message}</span>)}</td>
        </tr>)}</tbody>
      </table></div> : null}
      {preview.ready ? <>
        {preview.needsCashAccount ? <div className="form-field">
          <label htmlFor="import-cash-account">Onde entrou o dinheiro das vendas em dinheiro</label>
          <p id="import-cash-account-help" className="field-help">Uma gaveta ou conta bancária. As vendas da planilha não entram em caixa aberto: escolha onde o dinheiro está hoje. Se esse dinheiro já apareceu como sobra num fechamento de caixa, escolha outra conta para não contá-lo duas vezes.</p>
          <select id="import-cash-account" disabled={busy} value={cashAccount} onChange={event => { setCashAccount(event.target.value); setAccountError(null); }}
            aria-invalid={accountError ? true : undefined} aria-describedby={["import-cash-account-help", accountError ? "import-cash-account-error" : null].filter(Boolean).join(" ")}>
            <option value="">Escolha</option>{accounts.map(account => <option key={account.id} value={account.id}>{account.name}</option>)}</select>
          {accountError ? <p id="import-cash-account-error" className="field-error">{accountError}</p> : null}
        </div> : null}
        <div className="form-actions"><button type="button" disabled={busy} onClick={() => void send(true)}>{busy ? "Importando…" : `Importar ${preview.ready} ${preview.ready === 1 ? "venda" : "vendas"}`}</button></div>
        <p className="field-help">As vendas com erro ficam de fora. Corrija a planilha e envie de novo: as já importadas não se repetem.</p>
      </> : null}
    </> : null}
  </section>;
}
