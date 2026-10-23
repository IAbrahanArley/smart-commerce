import Link from "next/link";
import type { ReactNode } from "react";
import Icon, { type IconName } from "@/components/icons";

// Shared operational states use the existing panel, colour and button tokens.
export default function PageState({ title, children, kind = "empty", icon = "box", action, href, actionLabel, headingLevel = 2 }: {
  title: string; children: ReactNode; kind?: "empty" | "loading" | "error"; icon?: IconName;
  action?: () => void; href?: string; actionLabel?: string;
  headingLevel?: 2 | 3;
}) {
  const Heading = headingLevel === 3 ? "h3" : "h2";
  return <div className={`page-state page-state-${kind}`} role={kind === "error" ? "alert" : "status"}>
    <span className="page-state-icon"><Icon name={icon} /></span>
    <div className="page-state-content"><Heading>{title}</Heading><p>{children}</p>
      {action && actionLabel ? <button type="button" className="button-secondary" onClick={action}>{actionLabel}</button> : null}
      {href && actionLabel ? <Link className="button-link button-secondary" href={href}>{actionLabel}</Link> : null}
    </div>
  </div>;
}
