import Link from "next/link";
import type { ReactNode } from "react";
import NavIcon from "@/components/nav-icon";
import type { NavIcon as IconName } from "@/lib/navigation";

// Shared empty and error states (lucide icons, like the rest of the panel since phase 4).
export default function PageState({ title, children, kind = "empty", icon = "box", action, href, actionLabel, headingLevel = 2 }: {
  title: string; children: ReactNode; kind?: "empty" | "loading" | "error"; icon?: IconName;
  action?: () => void; href?: string; actionLabel?: string;
  headingLevel?: 2 | 3;
}) {
  const Heading = headingLevel === 3 ? "h3" : "h2";
  return <div className={`page-state page-state-${kind}`} role={kind === "error" ? "alert" : "status"}>
    <span className="page-state-icon" aria-hidden="true"><NavIcon name={icon} className="size-5" /></span>
    <div className="page-state-content"><Heading>{title}</Heading><p>{children}</p>
      {action && actionLabel ? <button type="button" className="button-secondary" onClick={action}>{actionLabel}</button> : null}
      {href && actionLabel ? <Link className="button-link button-secondary" href={href}>{actionLabel}</Link> : null}
    </div>
  </div>;
}
