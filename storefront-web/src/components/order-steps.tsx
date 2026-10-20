import Link from "next/link";

const STEPS = ["Carrinho", "Dados e entrega", "Pedido registrado"] as const;

// Progress through the order (DESIGN.md: Etapas do pedido). The current step carries aria-current="step";
// finished steps say so in text, not only by colour or the check mark.
export function OrderSteps({ current, cartUrl }: { current: 1 | 2; cartUrl?: string }) {
  return (
    <nav className="order-steps" aria-label="Etapas do pedido">
      <ol>
        {STEPS.map((label, index) => {
          const state = index < current ? "done" : index === current ? "current" : "next";
          return (
            <li key={label} data-state={state} aria-current={state === "current" ? "step" : undefined}>
              <span className="order-step-marker" aria-hidden="true">{state === "done" ? "✓" : index + 1}</span>
              {state === "done" && index === 0 && cartUrl ? <Link href={cartUrl} prefetch={false}>{label}</Link> : <span>{label}</span>}
              {state === "done" ? <span className="visually-hidden"> (concluída)</span> : null}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
