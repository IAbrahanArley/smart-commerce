"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";

export default function StoreDetail({ tenantId }: { tenantId: string }) {
  const [store, setStore] = useState<TenantStore | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404 || response.status === 403) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      setStore(await response.json()); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, attempt]);

  return <section className="stores-panel" aria-labelledby="store-title">
    <Link className="back-link" href="/conta">Voltar para minhas lojas</Link>
    <h1 id="store-title">{state === "ready" && store ? store.store.name : "Minha loja"}</h1>
    {state === "loading" ? <p role="status">Consultando sua loja…</p> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar sua loja. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar sua loja. Tente novamente.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar loja novamente</button></> : null}
    {state === "ready" && store ? <>
      <p className="notice">Em configuração. Sua loja ainda não está publicada e não possui vitrine pública.</p>
      <dl className="store-data"><dt>Empresa</dt><dd>{store.companyName}</dd><dt>Nome da loja</dt><dd>{store.store.name}</dd>
        <dt>Identificador da loja</dt><dd>{store.store.slug}</dd><dt>Seu acesso</dt><dd>Proprietário</dd></dl>
      <Link className="back-link" href={`/lojas/${encodeURIComponent(tenantId)}/produtos`}>Gerenciar produtos</Link>
    </> : null}
  </section>;
}
