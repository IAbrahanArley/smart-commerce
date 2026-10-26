import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

// KPI card adapted from Dashtail's dashboard statistics: icon in a soft circle, label, value and an
// optional detail. The icon is decorative; the label names the figure.
function KpiCard({ icon, label, value, detail, badge, children, className }: {
  icon: ReactNode; label: string; value: string; detail?: string; badge?: ReactNode; children?: ReactNode; className?: string;
}) {
  return <div className={cn("flex min-w-0 items-start gap-4 rounded-lg border border-border bg-card p-5 shadow-card", className)}>
    <span className="inline-flex size-11 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary [&_svg]:size-5" aria-hidden="true">{icon}</span>
    <div className="min-w-0 flex-1">
      <p className="m-0 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">{label} {badge}</p>
      <p className="m-0 mt-1 text-2xl font-bold leading-tight tabular-nums text-foreground [overflow-wrap:anywhere]">{value}</p>
      {detail ? <p className="m-0 mt-1 text-sm text-muted-foreground">{detail}</p> : null}
      {children}
    </div>
  </div>;
}

export { KpiCard };
