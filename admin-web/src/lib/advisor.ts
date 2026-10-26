// Store assistant (SC-027, ADR-0016 A5, A7, A12). Pure helpers, free of React and Next imports for
// the smoke test. The backend already refused any answer with a number that has no source; the
// admin only validates the shape, words the outcomes and lists the sources.

import { PERIODS } from "./finance.ts";

export type AdvisorStatus = { enabled: boolean; monthlyQuota: number; usedThisMonth: number; remaining: number };
export type AdvisorSource = { tool: string; period: string | null; metric: string | null };
export type AdvisorOutcome = "ANSWERED" | "REFUSED" | "UNGROUNDED";
export type AdvisorAnswer = { outcome: AdvisorOutcome; answer: string | null; sources: AdvisorSource[] };
export type Exchange = { id: number; question: string; result: AdvisorAnswer };

/** A7: at most 5 questions per conversation; the 4 earlier answered ones travel with the next. */
export const MAX_EXCHANGES = 5;
export const MAX_QUESTION = 500;

export const SUGGESTIONS = [
  "Como foi meu mês?",
  "O que está parado no estoque?",
  "Quais alertas devo olhar hoje?",
  "Quantos pedidos tive nos últimos 7 dias?",
] as const;

export const TOOL_LABELS: Record<string, string> = {
  relatorio_periodo: "Relatório financeiro",
  indicadores_dashboard: "Indicadores do painel",
  alertas: "Alertas",
  estoque_produtos: "Estoque",
  resumo_pedidos: "Resumo de pedidos",
};

const OUTCOMES: readonly AdvisorOutcome[] = ["ANSWERED", "REFUSED", "UNGROUNDED"];

function count(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

export function parseStatus(raw: unknown): AdvisorStatus | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const quota = count(value.monthlyQuota), used = count(value.usedThisMonth), remaining = count(value.remaining);
  if (typeof value.enabled !== "boolean" || quota === null || used === null || remaining === null) return null;
  return { enabled: value.enabled, monthlyQuota: quota, usedThisMonth: used, remaining };
}

/** Only answers with text have it; unknown tools and malformed sources are dropped. */
export function parseAnswer(raw: unknown): AdvisorAnswer | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  if (!OUTCOMES.includes(value.outcome as AdvisorOutcome)) return null;
  const outcome = value.outcome as AdvisorOutcome;
  const answer = typeof value.answer === "string" && value.answer.trim() ? value.answer.trim() : null;
  if (outcome === "ANSWERED" && !answer) return null;
  const sources: AdvisorSource[] = [];
  if (outcome === "ANSWERED" && Array.isArray(value.sources))
    for (const item of value.sources) {
      if (!item || typeof item !== "object") continue;
      const source = item as Record<string, unknown>;
      if (typeof source.tool !== "string" || !TOOL_LABELS[source.tool]) continue;
      sources.push({
        tool: source.tool,
        period: typeof source.period === "string" && PERIODS.some(period => period.key === source.period) ? source.period : null,
        metric: typeof source.metric === "string" && source.metric.trim() ? source.metric.trim().slice(0, 80) : null,
      });
    }
  return { outcome, answer: outcome === "ANSWERED" ? answer : null, sources };
}

/** "Relatório financeiro · Este mês · receita bruta". */
export function sourceText(source: AdvisorSource): string {
  const period = PERIODS.find(item => item.key === source.period)?.label;
  return [TOOL_LABELS[source.tool] ?? source.tool, period, source.metric].filter(Boolean).join(" · ");
}

export function outcomeText(outcome: AdvisorOutcome): string | null {
  switch (outcome) {
    case "REFUSED": return "O assistente não pode responder a essa pergunta. Pergunte sobre vendas, pedidos, estoque, finanças ou alertas da loja.";
    case "UNGROUNDED": return "Não foi possível responder com segurança a partir dos dados da loja. Tente perguntar de outro jeito.";
    default: return null;
  }
}

/** The earlier answered exchanges sent with the next question (A7), at most 4. */
export function historyFor(exchanges: Exchange[]): { question: string; answer: string }[] {
  return exchanges
    .filter(exchange => exchange.result.outcome === "ANSWERED" && exchange.result.answer)
    .slice(-(MAX_EXCHANGES - 1))
    .map(exchange => ({ question: exchange.question, answer: exchange.result.answer as string }));
}

export function questionError(text: string): string | null {
  const length = [...text.trim()].length;
  if (length === 0) return "Escreva uma pergunta.";
  if (length > MAX_QUESTION) return `A pergunta pode ter até ${MAX_QUESTION} caracteres.`;
  return null;
}

/** The message for a failed request, by HTTP status. */
export function askError(status: number): string {
  if (status === 400) return "Escreva uma pergunta de até 500 caracteres.";
  if (status === 429) return "A loja já usou todas as perguntas deste mês. A cota renova no início do próximo mês.";
  if (status === 503) return "O assistente está indisponível no momento. Tente novamente mais tarde.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível obter a resposta. Tente novamente.";
}

export function quotaText(status: AdvisorStatus): string {
  return status.remaining === 1
    ? `Resta 1 pergunta de ${status.monthlyQuota} neste mês.`
    : `Restam ${status.remaining} perguntas de ${status.monthlyQuota} neste mês.`;
}
