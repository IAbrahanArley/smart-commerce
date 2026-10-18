import Link from "next/link";
import type { ReactNode } from "react";

export default function PageHeader({ id, title, back, description, action }: { id: string; title: ReactNode; back?: { href: string; label: string }; description?: ReactNode; action?: ReactNode }) {
  return <header className="page-header">
    {back ? <Link className="back-link" href={back.href}>{back.label}</Link> : null}
    <div className="page-header-row">
      <div className="page-header-text">
        <h1 id={id}>{title}</h1>
        {description ? <p className="page-description">{description}</p> : null}
      </div>
      {action ? <div className="page-header-action">{action}</div> : null}
    </div>
  </header>;
}
