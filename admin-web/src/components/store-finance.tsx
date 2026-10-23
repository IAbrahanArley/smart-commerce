"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { formatDay } from "@/lib/expenses";
import { PERIODS, type FinanceReport, type ReportPeriodKey, missingText, percentText } from "@/lib/finance";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import PageState from "@/components/page-state";

// SC-021: the period's figures from the backend ledger (ADR-0015 F1–F5). Sale, receivable and cash
// are shown apart, and the margin says "parcial", with what is missing, whenever a component is
// unknown: it is never presented as a definitive profit.
export default function StoreFinance({ tenantId, period }: { tenantId: string; period: ReportPeriodKey }) {
  const [report, setReport] = useState<FinanceReport | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/finance?period=${period}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: FinanceReport = await response.json();
      if (controller.signal.aborted) return;
      setReport(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, period, attempt]);

  const feesUnknown = report?.missing.some(item => item.code === "FEES_UNAVAILABLE") ?? false;
  const rows: { label: string; value: string; strong?: boolean; note?: string }[] = report ? [
    { label: "Receita bruta (vendas pagas)", value: formatBRL(report.grossRevenue) },
    { label: "(−) Reembolsos", value: formatBRL(report.refunds) },
    { label: "(−) Taxas do Mercado Pago", value: feesUnknown && report.fees === "0.00" ? "Não informadas" : formatBRL(report.fees), note: feesUnknown ? "Parte das taxas ainda não foi informada pelo Mercado Pago." : undefined },
    { label: "= Receita líquida", value: formatBRL(report.netRevenue), strong: true },
    { label: "(−) Custo dos produtos vendidos (CMV)", value: formatBRL(report.cogs) },
    { label: "(−) Despesas variáveis", value: formatBRL(report.variableExpenses) },
    { label: "= Margem de contribuição", value: `${formatBRL(report.contributionMargin)} (${percentText(report.marginPercent)})`, strong: true },
  ] : [];

  return <section className="page" aria-labelledby="finance-title">
    <PageHeader id="finance-title" title="Financeiro" description="Resumo do período a partir dos lançamentos da loja. Vendas contam na data em que o pagamento foi confirmado; reembolsos, na data em que foram confirmados." />
    <form className="order-filter" method="get" action={`${storePath}/financeiro`}>
      <label htmlFor="finance-period">Período</label>
      <select id="finance-period" name="periodo" defaultValue={period}>
        {PERIODS.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}
      </select>
      <button type="submit">Ver período</button>
    </form>
    {state === "loading" ? <PageState kind="loading" icon="chart" title="Consultando o financeiro…">Carregando os lançamentos e os indicadores do período.</PageState> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o financeiro.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="chart" title="Não foi possível consultar o financeiro" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente carregar o resumo novamente.</PageState> : null}
    {state === "ready" && report ? <>
      <p>De {formatDay(report.period.firstDay)} a {formatDay(report.period.lastDay)} (horário de Brasília).</p>
      <div className="stat-grid">
        <Stat label="Pedidos pagos" value={new Intl.NumberFormat("pt-BR").format(report.paidOrders)} />
        <Stat label="Ticket médio" value={report.averageTicket ? formatBRL(report.averageTicket) : "—"} />
        <Stat label="A receber do Mercado Pago" value={formatBRL(report.receivable.amount)}
          detail={report.receivable.feesUnknown ? "Antes das taxas ainda não informadas; data de liberação desconhecida." : "Já descontadas as taxas informadas."} />
        <Stat label="Caixa do período" value={formatBRL(report.cashMovement)} detail="Dinheiro que entrou ou saiu no período: despesas pagas e valores liberados." />
      </div>
      <div className="data-table-wrap"><table className="data-table" role="table">
        <caption>Margem de contribuição do período {report.complete ? null : <StatusBadge tone="neutral">Parcial</StatusBadge>}</caption>
        <tbody role="rowgroup">{rows.map(row => <tr role="row" key={row.label}>
          <th role="rowheader" scope="row">{row.strong ? <strong>{row.label}</strong> : row.label}{row.note ? <span className="cell-hint">{row.note}</span> : null}</th>
          <td role="cell" data-label="Valor" className="numeric">{row.strong ? <strong>{row.value}</strong> : row.value}</td>
        </tr>)}</tbody>
      </table></div>
      {!report.complete ? <div className="notice">
        <p><strong>Margem parcial:</strong> faltam dados para um valor definitivo.</p>
        <ul>{report.missing.map(item => <li key={item.code}>{missingText(item)}</li>)}</ul>
        <p>Cadastre o custo dos produtos antes das próximas vendas. Alterar o custo no catálogo não completa os pedidos anteriores, que preservam o custo registrado no pedido. As taxas aparecem quando o Mercado Pago informar.</p>
      </div> : null}
      <dl className="order-facts">
        <div><dt>Despesas fixas do período</dt><dd className="numeric">{formatBRL(report.fixedExpenses)}</dd></div>
      </dl>
      <p className="field-help">Despesas fixas não entram na margem de contribuição. <Link href={`${storePath}/despesas`}>Ver despesas</Link>. Este resumo não é um demonstrativo contábil ou fiscal.</p>
    </> : null}
  </section>;
}

function Stat({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return <div className="stat-card"><div>
    <p className="field-help">{label}</p>
    <p><strong>{value}</strong></p>
    {detail ? <p className="field-help">{detail}</p> : null}
  </div></div>;
}
