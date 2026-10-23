"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useSignOut } from "@/lib/use-sign-out";
import { useAdminSession } from "@/components/session-guard";

const PUBLIC_PATHS = new Set(["/", "/entrar", "/cadastro", "/confirmar-email"]);

// Header disclosure with the signed-in e-mail. Shows nothing without a session; pages keep their own auth checks.
export default function AccountMenu() {
  const pathname = usePathname();
  const session = useAdminSession();
  const area = PUBLIC_PATHS.has(pathname) ? "public" : "app";
  const [email, setEmail] = useState("");
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === pathname;
  const { signOut, pending, error } = useSignOut();
  const root = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (session) return;
    const controller = new AbortController();
    fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", signal: controller.signal })
      .then(async response => setEmail(response.ok ? ((await response.json()) as { email: string }).email : ""))
      .catch(() => { if (!controller.signal.aborted) setEmail(""); });
    return () => controller.abort();
  }, [area, session]);

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) { if (event.key === "Escape") { setOpenAt(null); toggle.current?.focus(); } }
    function onPointer(event: PointerEvent) { if (!root.current?.contains(event.target as Node)) setOpenAt(null); }
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointer);
    return () => { document.removeEventListener("keydown", onKey); document.removeEventListener("pointerdown", onPointer); };
  }, [open]);

  const signedInEmail = session?.email ?? email;
  if (!signedInEmail) return null;
  return <div ref={root} className="account-menu">
    <button ref={toggle} type="button" className="account-menu-toggle" aria-expanded={open} aria-controls="account-menu-items" onClick={() => setOpenAt(open ? null : pathname)}>
      <span className="account-avatar" aria-hidden="true">{signedInEmail.charAt(0).toUpperCase()}</span> <span className="account-menu-label">Conta</span> <span className="account-menu-email">{signedInEmail}</span>
    </button>
    <div id="account-menu-items" className="account-menu-panel" hidden={!open}>
      <p className="account-menu-signed">Conectado como <span className="account-email-text">{signedInEmail}</span></p>
      <ul>
        <li><Link href="/lojas">Minhas lojas</Link></li>
        <li><Link href="/conta">Minha conta</Link></li>
      </ul>
      {error ? <p role="alert" className="field-error">{error}</p> : null}
      <button type="button" onClick={() => void signOut()} disabled={pending}>{pending ? "Saindo…" : "Sair da conta"}</button>
    </div>
  </div>;
}
