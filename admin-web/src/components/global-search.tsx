"use client";

import { useRouter } from "next/navigation";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import { Command } from "cmdk";
import { Package, Plus, ReceiptText, Search } from "lucide-react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type NavGroup, searchScore, storeBase } from "@/lib/navigation";
import { type OrderPage, ordersApiPath } from "@/lib/orders";
import NavIcon from "@/components/nav-icon";
import { Kbd } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";

type ProductHit = { id: string; name: string; sku: string };
type OrderHit = { id: string; number: number; buyerName: string | null; total: string };
type Results = { products: ProductHit[]; orders: OrderHit[] } | "loading" | "error" | null;

const ITEM = "flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-3 text-sm text-foreground data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground";
const HEADING = "[&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:pb-1.5 [&_[cmdk-group-heading]]:pt-3 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-wide [&_[cmdk-group-heading]]:text-muted-foreground";

// Global search (Dashtail header search, cmdk): Ctrl/Cmd+K opens it anywhere in the admin. Pages and
// actions come from the navigation; inside a store it also lists the store's products and its latest
// orders, loaded only when the search opens. The backend authorizes every request.
export default function GlobalSearch({ groups, tenantId }: { groups: NavGroup[]; tenantId?: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<Results>(null);
  const loadedFor = useRef<string | null>(null);

  function load() {
    if (!tenantId || loadedFor.current === tenantId) return;
    loadedFor.current = tenantId;
    setResults("loading");
    const api = `/api/tenants/${encodeURIComponent(tenantId)}`;
    Promise.all([apiRequest(`${api}/products`), apiRequest(ordersApiPath(tenantId, null, null))]).then(async ([products, orders]) => {
      if (!products.ok || !orders.ok) throw new Error();
      const productList = (await products.json()) as ProductHit[];
      const orderPage = (await orders.json()) as OrderPage;
      setResults({
        products: productList.map(({ id, name, sku }) => ({ id, name, sku })),
        orders: orderPage.orders.map(({ id, number, buyerName, total }) => ({ id, number, buyerName, total })),
      });
    }).catch(() => { loadedFor.current = null; setResults("error"); });
  }

  function change(next: boolean) {
    setOpen(next);
    if (next) load();
  }

  const openFromShortcut = useEffectEvent(() => change(true));
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); openFromShortcut(); }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  function go(href: string) {
    setOpen(false);
    router.push(href);
  }

  const base = tenantId ? storeBase(tenantId) : null;
  const found = results && typeof results === "object" ? results : null;
  return <>
    <button type="button" onClick={() => change(true)} aria-keyshortcuts="Control+K Meta+K"
      className="flex h-10 min-h-0 w-full max-w-sm items-center gap-2.5 rounded-md border border-border bg-background px-3 py-0 text-sm font-normal text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-2 focus-visible:ring-ring">
      <Search className="size-4 shrink-0" aria-hidden="true" />
      <span className="flex-1 truncate text-left">Buscar páginas{tenantId ? ", produtos e pedidos" : ""}…</span>
      <Kbd className="hidden md:inline-flex">Ctrl K</Kbd>
    </button>
    <Dialog open={open} onOpenChange={change}>
      <DialogContent className="overflow-hidden p-0">
        <DialogTitle className="sr-only">Busca</DialogTitle>
        <DialogDescription className="sr-only">Digite para filtrar. Use as setas para escolher e Enter para abrir.</DialogDescription>
        <Command label="Busca" filter={searchScore} className="flex max-h-[70vh] flex-col">
          <div className="flex items-center gap-2.5 border-b border-border px-4 pr-14">
            <Search className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <Command.Input placeholder="O que você procura?" className="h-14 w-full min-w-0 border-0 bg-transparent text-base text-foreground outline-none placeholder:text-muted-foreground" />
          </div>
          <Command.List className={`overflow-y-auto p-2 ${HEADING}`}>
            <Command.Empty className="px-3 py-8 text-center text-sm text-muted-foreground">Nada encontrado.</Command.Empty>
            {base ? <Command.Group heading="Ações">
              <Command.Item className={ITEM} value="novo produto cadastrar" onSelect={() => go(`${base}/produtos/novo`)}><Plus aria-hidden="true" /> Novo produto</Command.Item>
            </Command.Group> : null}
            {groups.map(group => <Command.Group key={group.label} heading={group.label}>
              {group.items.map(item => <Command.Item key={item.href} className={ITEM} value={`${item.label} ${item.keywords ?? ""}`} onSelect={() => go(item.href)}>
                <NavIcon name={item.icon} /> {item.label}
              </Command.Item>)}
            </Command.Group>)}
            {results === "loading" ? <Command.Loading><p className="m-0 px-3 py-3 text-sm text-muted-foreground">Carregando produtos e pedidos…</p></Command.Loading> : null}
            {results === "error" ? <p role="status" className="m-0 px-3 py-3 text-sm text-muted-foreground">Não foi possível carregar produtos e pedidos. Feche e abra a busca para tentar de novo.</p> : null}
            {base && found && found.products.length > 0 ? <Command.Group heading="Produtos">
              {found.products.map(product => <Command.Item key={product.id} className={ITEM} value={`produto ${product.name} ${product.sku}`} onSelect={() => go(`${base}/produtos/${encodeURIComponent(product.id)}`)}>
                <Package aria-hidden="true" /> <span className="min-w-0 flex-1 truncate">{product.name}</span> <span className="text-xs text-muted-foreground">{product.sku}</span>
              </Command.Item>)}
            </Command.Group> : null}
            {base && found && found.orders.length > 0 ? <Command.Group heading="Pedidos recentes">
              {found.orders.map(order => <Command.Item key={order.id} className={ITEM} value={`pedido ${order.number} ${order.buyerName ?? ""}`} onSelect={() => go(`${base}/pedidos/${encodeURIComponent(order.id)}`)}>
                <ReceiptText aria-hidden="true" /> <span className="min-w-0 flex-1 truncate">Pedido nº {order.number}{order.buyerName ? ` · ${order.buyerName}` : ""}</span> <span className="text-xs text-muted-foreground">{formatBRL(order.total)}</span>
              </Command.Item>)}
            </Command.Group> : null}
          </Command.List>
        </Command>
      </DialogContent>
    </Dialog>
  </>;
}
