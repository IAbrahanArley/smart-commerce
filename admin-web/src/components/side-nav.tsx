"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";

export type SideNavItem = { href: string; label: string; current: boolean };

// Shared frame navigation (DESIGN.md: Moldura e padrões do admin). Links never prove authorization.
export default function SideNav({ label, title, items, secondary = [] }: { label: string; title: ReactNode; items: SideNavItem[]; secondary?: SideNavItem[] }) {
  const pathname = usePathname();
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === pathname;
  const link = (item: SideNavItem) => <li key={item.href}><Link href={item.href} aria-current={item.current ? "page" : undefined}>{item.label}</Link></li>;

  return <nav className="side-nav" aria-label={label}>
    <div className="side-nav-top">
      <p className="side-nav-title">{title}</p>
      <button type="button" className="side-nav-toggle" aria-expanded={open} aria-controls="side-nav-items" onClick={() => setOpenAt(open ? null : pathname)}>Menu</button>
    </div>
    <div id="side-nav-items" className="side-nav-items" data-open={open}>
      <ul>{items.map(link)}</ul>
      {secondary.length ? <ul className="side-nav-secondary">{secondary.map(link)}</ul> : null}
    </div>
  </nav>;
}

export function accountItems(pathname: string): SideNavItem[] {
  return [
    { href: "/lojas", label: "Minhas lojas", current: pathname === "/lojas" },
    { href: "/conta", label: "Minha conta", current: pathname === "/conta" },
  ];
}
