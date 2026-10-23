"use client";

import { usePathname } from "next/navigation";
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { apiRequest } from "@/lib/auth-api";
import { isProtectedPath, SESSION_INVALIDATED } from "@/lib/session-events";

type Session = { id: string; email: string };
type Check = { pathname: string; session: Session | null; error: boolean };
const SessionContext = createContext<Session | null>(null);

export function useAdminSession() { return useContext(SessionContext); }

// This gate controls presentation only. Every API still authorizes identity and tenant on the backend.
export default function SessionGuard({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (!isProtectedPath(pathname)) return children;
  // A fresh instance prevents a previously verified route from being reused after public login/navigation.
  return <ProtectedSession key={pathname} pathname={pathname}>{children}</ProtectedSession>;
}

function ProtectedSession({ pathname, children }: { pathname: string; children: ReactNode }) {
  const [check, setCheck] = useState<Check | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [retrying, setRetrying] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const identity = useRef<string | null>(null);

  useEffect(() => {
    let active = true;
    let ending = false;
    let request = 0;
    let controller: AbortController | undefined;

    function invalidate() {
      if (!active) return;
      // Invalidate pending identity callbacks before any old response can reveal the admin again.
      ending = true;
      active = false;
      controller?.abort();
      setCheck(null);
      if (root.current) root.current.hidden = true;
      window.location.replace("/entrar");
    }

    async function validate() {
      if (!active) return;
      const current = ++request;
      controller?.abort();
      controller = new AbortController();
      try {
        const response = await apiRequest("/api/auth/me", { signal: controller.signal });
        if (!active || current !== request) return;
        if (response.status === 401) { invalidate(); return; }
        if (!response.ok) throw new Error();
        const session: unknown = await response.json();
        if (!active || current !== request) return;
        if (!isSession(session)) throw new Error();
        if (identity.current !== null && identity.current !== session.id) { invalidate(); return; }
        identity.current = session.id;
        setCheck({ pathname, session, error: false });
        if (root.current) root.current.hidden = false;
      } catch {
        if (!active || current !== request || controller.signal.aborted) return;
        setCheck(previous => ({ pathname, session: previous?.pathname === pathname ? previous.session : null, error: true }));
        if (root.current) root.current.hidden = false;
      } finally {
        if (active && current === request) setRetrying(false);
      }
    }

    function onFocus() { if (document.visibilityState === "visible") void validate(); }
    function onBeforeUnload(event: BeforeUnloadEvent) {
      // The gate subscribes before mounting forms. A revoked session must leave without a draft guard.
      if (ending) event.stopImmediatePropagation();
    }
    function onPageHide() { if (root.current) root.current.hidden = true; }
    function onPageShow(event: PageTransitionEvent) {
      if (!event.persisted) return;
      if (!active) { window.location.replace("/entrar"); return; }
      void validate();
    }

    window.addEventListener(SESSION_INVALIDATED, invalidate);
    window.addEventListener("beforeunload", onBeforeUnload, { capture: true });
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("pageshow", onPageShow);
    void validate();
    return () => {
      active = false;
      controller?.abort();
      window.removeEventListener(SESSION_INVALIDATED, invalidate);
      window.removeEventListener("beforeunload", onBeforeUnload, { capture: true });
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
      window.removeEventListener("pagehide", onPageHide);
      window.removeEventListener("pageshow", onPageShow);
    };
  }, [pathname, attempt]);

  const current = check?.pathname === pathname ? check : null;
  const ErrorContainer = current?.session ? "div" : "main";
  return <div ref={root}>
    {!current ? <main id="conteudo" className="site-main" tabIndex={-1}><section className="page-panel" aria-labelledby="session-title">
      <h1 id="session-title">Verificando sua sessão</h1><p role="status">Aguarde enquanto confirmamos seu acesso…</p>
    </section></main> : null}
    {current?.error ? <ErrorContainer id={current.session ? undefined : "conteudo"} className="site-main" tabIndex={-1}><section className="page-panel" aria-labelledby="session-error-title">
      <h1 id="session-error-title">Não foi possível confirmar seu acesso</h1>
      <p role="alert">Verifique a conexão e tente novamente. Seus dados serão exibidos após a confirmação da sessão.</p>
      <button type="button" disabled={retrying} onClick={() => { setRetrying(true); setAttempt(value => value + 1); }}>{retrying ? "Verificando sessão…" : "Tentar novamente"}</button>
    </section></ErrorContainer> : null}
    {current?.session ? <SessionContext value={current.session}><div hidden={current.error}>{children}</div></SessionContext> : null}
  </div>;
}

function isSession(value: unknown): value is Session {
  return typeof value === "object" && value !== null && "id" in value && typeof value.id === "string"
    && value.id.length > 0 && "email" in value && typeof value.email === "string" && value.email.length > 0;
}
