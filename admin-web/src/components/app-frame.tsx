"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, useSyncExternalStore, type ReactNode } from "react";
import { Menu, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import AccountMenu from "@/components/account-menu";
import GlobalSearch from "@/components/global-search";
import NavIcon from "@/components/nav-icon";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { type NavGroup, parseCollapsed, SIDEBAR_KEY } from "@/lib/navigation";
import { cn } from "@/lib/utils";

export type { NavGroup, NavItem } from "@/lib/navigation";
export { accountItems } from "@/lib/navigation";

// Collapsed preference in localStorage (per viewer); the server and a blocked storage render expanded.
const listeners = new Set<() => void>();
function readCollapsed(): boolean {
  try { return parseCollapsed(window.localStorage.getItem(SIDEBAR_KEY)); } catch { return false; }
}
function writeCollapsed(value: boolean) {
  try { window.localStorage.setItem(SIDEBAR_KEY, String(value)); } catch { /* the toggle still works for this page */ }
  listeners.forEach(listener => listener());
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  window.addEventListener("storage", listener);
  return () => { listeners.delete(listener); window.removeEventListener("storage", listener); };
}

// Admin frame (DESIGN.md, "Design system Dashtail"): sidebar that collapses to icons on desktop and
// becomes a drawer below 64rem, header with the global search and the account menu. Links never
// prove authorization.
export default function AppFrame({ navLabel, context, groups, tenantId, children }: {
  navLabel: string; context?: ReactNode; groups: NavGroup[]; tenantId?: string; children: ReactNode;
}) {
  const pathname = usePathname();
  const collapsed = useSyncExternalStore(subscribe, readCollapsed, () => false);
  const [drawerAt, setDrawerAt] = useState<string | null>(null);
  const drawerOpen = drawerAt === pathname;

  return <TooltipProvider delayDuration={150}>
    <div className="flex min-h-screen flex-1 bg-background">
      <aside className={cn("sticky top-0 hidden h-screen shrink-0 border-r border-border bg-card transition-[width] duration-200 motion-reduce:transition-none lg:block", collapsed ? "w-[4.5rem]" : "w-64")}>
        <Sidebar navLabel={navLabel} context={context} groups={groups} collapsed={collapsed} />
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-card px-4 lg:px-6">
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Abrir menu" aria-haspopup="dialog" aria-expanded={drawerOpen} onClick={() => setDrawerAt(pathname)}>
            <Menu className="size-5" aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon" className="hidden lg:inline-flex" aria-label={collapsed ? "Expandir menu lateral" : "Recolher menu lateral"} aria-pressed={collapsed} onClick={() => writeCollapsed(!collapsed)}>
            {collapsed ? <PanelLeftOpen className="size-5" aria-hidden="true" /> : <PanelLeftClose className="size-5" aria-hidden="true" />}
          </Button>
          <div className="min-w-0 flex-1"><GlobalSearch groups={groups} tenantId={tenantId} /></div>
          <AccountMenu />
        </header>
        <main id="conteudo" className="app-main" tabIndex={-1}>{children}</main>
        <footer className="app-footer"><span translate="no">Smart Commerce</span></footer>
      </div>
    </div>
    <Dialog open={drawerOpen} onOpenChange={open => setDrawerAt(open ? pathname : null)}>
      <DialogContent side="left" closeLabel="Fechar menu">
        <DialogTitle className="sr-only">Menu</DialogTitle>
        <DialogDescription className="sr-only">Navegação do painel.</DialogDescription>
        <Sidebar navLabel={navLabel} context={context} groups={groups} collapsed={false} onNavigate={() => setDrawerAt(null)} />
      </DialogContent>
    </Dialog>
  </TooltipProvider>;
}

function Sidebar({ navLabel, context, groups, collapsed, onNavigate }: {
  navLabel: string; context?: ReactNode; groups: NavGroup[]; collapsed: boolean; onNavigate?: () => void;
}) {
  return <div className="flex h-full flex-col">
    <div className={cn("flex h-16 shrink-0 items-center gap-3 border-b border-border", collapsed ? "justify-center px-2" : "px-5")}>
      <Link href="/lojas" onClick={onNavigate} className="flex min-w-0 items-center gap-3 rounded-md text-foreground no-underline hover:no-underline focus-visible:ring-2 focus-visible:ring-ring">
        <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground" aria-hidden="true">SC</span>
        <span className={cn("min-w-0 leading-tight", collapsed && "sr-only")}>
          <span className="block truncate font-bold" translate="no">Smart Commerce</span>
          <span className="block text-xs text-muted-foreground">Administração</span>
        </span>
      </Link>
    </div>
    {context && !collapsed ? <div className="mx-4 mt-4 rounded-lg border border-border bg-background px-3 py-2.5 text-sm font-semibold">{context}</div> : null}
    <nav aria-label={navLabel} className={cn("flex-1 overflow-y-auto py-4", collapsed ? "px-2" : "px-3")}>
      {groups.map((group, index) => <div key={group.label} className={cn(index > 0 && "mt-5")}>
        {collapsed
          ? (index > 0 ? <hr className="mx-2 mb-3 border-border" /> : null)
          : <p className="m-0 mb-1.5 px-2.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground" id={`nav-group-${index}`}>{group.label}</p>}
        <ul className="m-0 grid list-none gap-1 p-0" aria-labelledby={collapsed ? undefined : `nav-group-${index}`} aria-label={collapsed ? group.label : undefined}>
          {group.items.map(item => {
            const link = <Link href={item.href} onClick={onNavigate} aria-current={item.current ? "page" : undefined} className={cn(
              "flex min-h-10 items-center gap-3 rounded-md text-sm font-medium text-muted-foreground no-underline transition-colors hover:bg-accent hover:text-accent-foreground hover:no-underline focus-visible:ring-2 focus-visible:ring-ring aria-[current=page]:bg-primary aria-[current=page]:text-primary-foreground",
              collapsed ? "mx-auto size-11 justify-center" : "px-2.5")}>
              <NavIcon name={item.icon} className="size-5 shrink-0" />
              <span className={cn("truncate", collapsed && "sr-only")}>{item.label}</span>
            </Link>;
            return <li key={item.href}>{collapsed
              ? <Tooltip><TooltipTrigger asChild>{link}</TooltipTrigger><TooltipContent side="right">{item.label}</TooltipContent></Tooltip>
              : link}</li>;
          })}
        </ul>
      </div>)}
    </nav>
  </div>;
}
