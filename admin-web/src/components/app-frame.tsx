"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import AccountMenu from "@/components/account-menu";
import Icon, { type IconName } from "@/components/icons";

export type NavItem = { href: string; label: string; icon: IconName; current: boolean };
export type NavGroup = { label: string; items: NavItem[] };

// Admin frame (DESIGN.md: Moldura e padrões do admin): top bar, side navigation and content.
// Below 64rem the "Menu" button discloses the same navigation. Links never prove authorization.
export default function AppFrame({ navLabel, context, groups, children }: { navLabel: string; context?: ReactNode; groups: NavGroup[]; children: ReactNode }) {
  const pathname = usePathname();
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === pathname;

  return <div className="app-frame">
    <header className="app-topbar">
      <button type="button" className="app-menu-toggle" aria-expanded={open} aria-controls="app-sidebar" onClick={() => setOpenAt(open ? null : pathname)}>
        <Icon name="menu" /> Menu
      </button>
      <p className="app-topbar-brand"><Link className="brand" href="/" translate="no">Smart Commerce</Link></p>
      <AccountMenu />
    </header>
    <nav id="app-sidebar" className="app-sidebar" aria-label={navLabel} data-open={open}>
      <p className="app-sidebar-brand"><Link className="brand" href="/" translate="no">Smart Commerce</Link> <span>Administração</span></p>
      {context ? <div className="app-sidebar-context">{context}</div> : null}
      {groups.map((group, index) => <div className="app-nav-group" key={group.label}>
        <p className="app-nav-group-label" id={`app-nav-group-${index}`}>{group.label}</p>
        <ul aria-labelledby={`app-nav-group-${index}`}>{group.items.map(item => <li key={item.href}>
          <Link href={item.href} aria-current={item.current ? "page" : undefined}><Icon name={item.icon} /> {item.label}</Link>
        </li>)}</ul>
      </div>)}
    </nav>
    <div className="app-content">
      <main id="conteudo" className="app-main" tabIndex={-1}>{children}</main>
      <footer className="app-footer">Smart Commerce · Ambiente de desenvolvimento</footer>
    </div>
  </div>;
}

export function accountItems(pathname: string): NavItem[] {
  return [
    { href: "/lojas", label: "Minhas lojas", icon: "store", current: pathname === "/lojas" },
    { href: "/conta", label: "Minha conta", icon: "user", current: pathname === "/conta" },
  ];
}
