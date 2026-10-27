"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Building2, ChevronDown, LogOut, User } from "lucide-react";
import { useSignOut } from "@/lib/use-sign-out";
import { useAdminSession } from "@/components/session-guard";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";

const PUBLIC_PATHS = new Set(["/", "/entrar", "/cadastro", "/confirmar-email"]);

// Header account menu (Dashtail profile dropdown) with the signed-in e-mail. Shows nothing without a
// session; pages keep their own auth checks.
export default function AccountMenu() {
  const pathname = usePathname();
  const session = useAdminSession();
  const area = PUBLIC_PATHS.has(pathname) ? "public" : "app";
  const [email, setEmail] = useState("");
  const { signOut, pending, error } = useSignOut();

  useEffect(() => {
    if (session) return;
    const controller = new AbortController();
    fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", signal: controller.signal })
      .then(async response => setEmail(response.ok ? ((await response.json()) as { email: string }).email : ""))
      .catch(() => { if (!controller.signal.aborted) setEmail(""); });
    return () => controller.abort();
  }, [area, session]);

  const signedInEmail = session?.email ?? email;
  if (!signedInEmail) return null;
  return <div className="flex min-w-0 items-center gap-3">
    {error ? <p role="alert" className="m-0 text-sm text-destructive">{error}</p> : null}
    <DropdownMenu>
      <DropdownMenuTrigger className="flex h-11 min-h-0 min-w-0 items-center gap-2.5 rounded-md bg-transparent px-2 py-0 text-sm font-medium text-foreground hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring data-[state=open]:bg-accent">
        <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground" aria-hidden="true">{signedInEmail.charAt(0).toUpperCase()}</span>
        <span className="sr-only">Conta: </span>
        <span className="hidden max-w-56 truncate sm:block">{signedInEmail}</span>
        <ChevronDown className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <span className="block text-xs text-muted-foreground">Conectado como</span>
          <span className="block truncate font-semibold">{signedInEmail}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild><Link href="/lojas"><Building2 aria-hidden="true" /> Minhas lojas</Link></DropdownMenuItem>
        <DropdownMenuItem asChild><Link href="/conta"><User aria-hidden="true" /> Minha conta</Link></DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled={pending} onSelect={() => void signOut()}><LogOut aria-hidden="true" /> {pending ? "Saindo…" : "Sair da conta"}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </div>;
}
