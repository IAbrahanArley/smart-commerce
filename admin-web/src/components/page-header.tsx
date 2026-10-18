import Link from "next/link";
import type { ReactNode } from "react";

export type Crumb = { href: string; label: ReactNode };

// Breadcrumb lists ancestors as links; the current page closes the trail as plain text.
export default function PageHeader({ id, title, breadcrumbs, current, description, action }: { id: string; title: ReactNode; breadcrumbs?: Crumb[]; current?: ReactNode; description?: ReactNode; action?: ReactNode }) {
  return <header className="page-header">
    {breadcrumbs?.length ? <nav aria-label="Trilha de navegação" className="breadcrumb"><ol>
      {breadcrumbs.map(crumb => <li key={crumb.href}><Link href={crumb.href}>{crumb.label}</Link></li>)}
      <li aria-current="page">{current ?? title}</li>
    </ol></nav> : null}
    <div className="page-header-row">
      <div className="page-header-text">
        <h1 id={id}>{title}</h1>
        {description ? <p className="page-description">{description}</p> : null}
      </div>
      {action ? <div className="page-header-action">{action}</div> : null}
    </div>
  </header>;
}
