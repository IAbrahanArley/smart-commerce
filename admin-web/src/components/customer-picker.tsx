"use client";

import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { type Customer, nameError, phoneDigits, phoneText } from "@/lib/credit";

// SC-055: find a customer of the store by name, e-mail or phone, or register one with name and
// phone. The search travels in the request body, never in the URL. An employee sees phones masked
// (the backend decides). Used by the credit sale and by the receipt.
export default function CustomerPicker({ tenantId, idPrefix, disabled, invalid, describedBy, onSelect }: {
  tenantId: string; idPrefix: string; disabled?: boolean; invalid?: boolean; describedBy?: string; onSelect: (customer: Customer) => void;
}) {
  const [search, setSearch] = useState("");
  const [found, setFound] = useState<{ query: string; items: Customer[] } | null>(null);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [errors, setErrors] = useState<{ name?: string; phone?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const nameRef = useRef<HTMLInputElement>(null);
  const base = `/api/tenants/${encodeURIComponent(tenantId)}/customers`;

  useEffect(() => {
    const text = search.trim();
    if ([...text].length < 2) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      apiRequest(`${base}/search`, { method: "POST", body: { q: text }, signal: controller.signal }).then(async response => {
        if (!response.ok) throw new Error();
        const items: Customer[] = await response.json();
        if (!controller.signal.aborted) setFound({ query: text, items });
      }).catch(() => { if (!controller.signal.aborted) setFound({ query: text, items: [] }); });
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [base, search]);

  useEffect(() => { if (creating) nameRef.current?.focus(); }, [creating]);

  const query = search.trim();
  const results = found && found.query === query && [...query].length >= 2 ? found.items : null;

  async function create() {
    if (busy) return;
    const next: typeof errors = {};
    const nameProblem = nameError(name);
    if (nameProblem) next.name = nameProblem;
    const digits = phoneDigits(phone);
    if (!digits) next.phone = "Informe o telefone com DDD, de 10 a 15 dígitos.";
    setErrors(next);
    if (next.name || next.phone) { document.getElementById(next.name ? `${idPrefix}-new-name` : `${idPrefix}-new-phone`)?.focus(); return; }
    setBusy(true);
    try {
      const response = await apiRequest(base, { method: "POST", body: { requestId, name: name.trim(), phone: digits } });
      if (response.ok) {
        const customer: Customer = await response.json();
        setCreating(false); setName(""); setPhone(""); setSearch(""); setRequestId(crypto.randomUUID());
        onSelect(customer); return;
      }
      const answer = await response.json().catch(() => null) as { message?: string; code?: string } | null;
      if (answer?.code === "REQUEST_CONFLICT") setRequestId(crypto.randomUUID());
      setErrors({ form: response.status === 400 ? "Confira o nome e o telefone." : answer?.message ?? requestError(response.status) });
    } catch {
      setErrors({ form: "Não foi possível cadastrar o cliente. Tente de novo: a mesma tentativa não cadastra duas vezes." });
    } finally { setBusy(false); }
  }

  const off = disabled || busy;
  return <div className="form-field">
    <label htmlFor={`${idPrefix}-search`}>Cliente</label>
    <p id={`${idPrefix}-search-help`} className="field-help">Busque pelo nome ou pelo telefone completo, com DDD.</p>
    <input id={`${idPrefix}-search`} type="search" disabled={off} value={search} maxLength={120} autoComplete="off" onChange={event => setSearch(event.target.value)}
      aria-invalid={invalid ? true : undefined} aria-describedby={[`${idPrefix}-search-help`, describedBy].filter(Boolean).join(" ")} />
    {results ? <ul className="m-0 grid list-none gap-2 p-0" aria-label="Clientes encontrados" aria-live="polite">
      {results.length ? results.map(customer => <li key={customer.id}>
        <button type="button" className="button-secondary" disabled={off} onClick={() => { setSearch(""); onSelect(customer); }}>
          {customer.name} · {phoneText(customer.phone)}
        </button>
      </li>) : <li className="field-help">Nenhum cliente encontrado.</li>}
    </ul> : null}
    {creating ? <fieldset className="form-field">
      <legend>Cadastrar cliente</legend>
      <label htmlFor={`${idPrefix}-new-name`}>Nome</label>
      <input ref={nameRef} id={`${idPrefix}-new-name`} disabled={off} value={name} maxLength={240} autoComplete="off" onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); void create(); } }} onChange={event => { setName(event.target.value); setErrors(current => ({ ...current, name: undefined })); }}
        aria-invalid={errors.name ? true : undefined} aria-describedby={errors.name ? `${idPrefix}-new-name-error` : undefined} />
      {errors.name ? <p id={`${idPrefix}-new-name-error`} className="field-error">{errors.name}</p> : null}
      <label htmlFor={`${idPrefix}-new-phone`}>Telefone com DDD</label>
      <input id={`${idPrefix}-new-phone`} disabled={off} value={phone} inputMode="tel" maxLength={24} autoComplete="off" onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); void create(); } }} onChange={event => { setPhone(event.target.value); setErrors(current => ({ ...current, phone: undefined })); }}
        aria-invalid={errors.phone ? true : undefined} aria-describedby={errors.phone ? `${idPrefix}-new-phone-error` : undefined} />
      {errors.phone ? <p id={`${idPrefix}-new-phone-error`} className="field-error">{errors.phone}</p> : null}
      {errors.form ? <p className="field-error" role="alert">{errors.form}</p> : null}
      <div className="form-actions">
        <button type="button" disabled={off} onClick={() => void create()}>{busy ? "Cadastrando…" : "Cadastrar e escolher"}</button>
        <button type="button" className="button-secondary" disabled={off} onClick={() => { setCreating(false); setErrors({}); }}>Voltar à busca</button>
      </div>
    </fieldset> : <button type="button" className="button-secondary" disabled={off} onClick={() => setCreating(true)}>Cadastrar novo cliente</button>}
  </div>;
}
