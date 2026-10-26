"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { type ProfileErrors, type ProfileFields, type StoreProfile, logoFileError, profileErrors, profileFields, profilePayload } from "@/lib/store-profile";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import { useConfirm } from "@/components/confirm-dialog";
import { Loading } from "@/components/ui/loading";

// MVP block 1b (PRD item 10): what the storefront shows about the store. Contacts are public by
// choice: empty fields are not shown. Name/contacts and the logo share the profile version, so a
// change made in another session is never overwritten.
export default function StoreProfileEditor({ tenantId }: { tenantId: string }) {
  const [saved, setSaved] = useState<StoreProfile | null>(null);
  const [fields, setFields] = useState<ProfileFields | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [errors, setErrors] = useState<ProfileErrors>({});
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const [logo, setLogo] = useState<File | null>(null);
  const [logoError, setLogoError] = useState<string | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const logoForm = useRef<HTMLFormElement>(null);
  const { confirm, dialog } = useConfirm();
  const path = `/api/tenants/${encodeURIComponent(tenantId)}/profile`;
  const dirty = !!saved && !!fields && JSON.stringify(fields) !== JSON.stringify(profileFields(saved));

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(path, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: StoreProfile = await response.json();
      if (controller.signal.aborted) return;
      setSaved(current); setFields(profileFields(current)); setConflict(false); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [path, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  function update<K extends keyof ProfileFields>(field: K, value: string) {
    setFields(current => current ? { ...current, [field]: value } : current);
    setErrors(current => ({ ...current, [field]: undefined }));
  }

  async function reload() {
    if (dirty && !(await confirm({ title: "Carregar os dados atuais?", message: "Suas alterações não salvas serão descartadas.", confirmLabel: "Descartar e carregar", tone: "danger" }))) return;
    setFeedback(null); setErrors({}); setState("loading"); setAttempt(current => current + 1);
  }

  /** One request at a time; a 409 freezes the form until the current data is loaded. */
  async function send(request: () => Promise<Response>, success: string, keepFields: boolean) {
    if (submitting.current || conflict) return;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await request();
      if (response.status === 409) { setConflict(true); setFeedback({ text: "Os dados da loja foram alterados em outra sessão. Nada foi sobrescrito. Carregue os dados atuais antes de editar novamente.", error: true }); return; }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de salvar.", error: true }); return; }
      if (response.status === 404 || response.status === 403) { setFeedback({ text: "Loja indisponível para esta solicitação. Volte para suas lojas ou tente novamente.", error: true }); return; }
      if (response.status === 400 || response.status === 413) {
        const body: { message?: unknown } = await response.json().catch(() => ({}));
        setFeedback({ text: typeof body.message === "string" && body.message.trim() ? body.message : "Dados inválidos. Confira os campos e tente novamente.", error: true }); return;
      }
      if (!response.ok) { setFeedback({ text: requestError(response.status), error: true }); return; }
      const current: StoreProfile = await response.json();
      setSaved(current);
      // A logo change keeps unsaved text edits; a profile save shows exactly what was stored.
      if (!keepFields) setFields(profileFields(current));
      setFeedback({ text: success, error: false });
    } catch { setConflict(true); setFeedback({ text: "Não foi possível confirmar o salvamento. Carregue os dados atuais antes de tentar novamente.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!saved || !fields) return;
    const found = profileErrors(fields);
    setErrors(found);
    const first = (["name", "contactEmail", "whatsapp", "instagram"] as const).find(field => found[field]);
    if (first) { document.getElementById(`profile-${first}`)?.focus(); return; }
    void send(() => apiRequest(path, { method: "PUT", body: profilePayload(fields, saved.version) }), "Dados da loja salvos.", false);
  }

  function uploadLogo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!saved) return;
    const problem = logoFileError(logo);
    setLogoError(problem);
    if (problem || !logo) { document.getElementById("profile-logo")?.focus(); return; }
    const body = new FormData(); body.set("file", logo); body.set("version", String(saved.version));
    void send(() => apiRequest(`${path}/logo`, { method: "POST", body }), "Logo atualizado.", true)
      .then(() => { setLogo(null); logoForm.current?.reset(); });
  }

  async function removeLogo() {
    if (!saved || !(await confirm({ title: "Remover o logo?", message: "O logo sai do painel e da vitrine. Para usá-lo de novo, envie o arquivo outra vez.", confirmLabel: "Remover logo", tone: "danger" }))) return;
    void send(() => apiRequest(`${path}/logo?version=${saved.version}`, { method: "DELETE" }), "Logo removido.", true);
  }

  const disabled = busy || conflict;
  const field = (name: keyof ProfileFields, label: string, help: string, control: React.ReactNode) => <div className="form-field">
    <label htmlFor={`profile-${name}`}>{label}</label>
    <p id={`profile-${name}-help`} className="field-help">{help}</p>
    {control}
    {errors[name] ? <p id={`profile-${name}-error`} className="field-error">{errors[name]}</p> : null}
  </div>;
  const described = (name: keyof ProfileFields) => `profile-${name}-help${errors[name] ? ` profile-${name}-error` : ""}`;

  return <section className="page store-profile" aria-labelledby="profile-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="profile-title" title="Dados da loja" description="Nome, logo e contatos que aparecem na vitrine publicada. Contatos em branco não são mostrados." />
    {state === "loading" ? <Loading context="dados da loja" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para editar os dados da loja.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="store" title="Não foi possível consultar os dados da loja" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {conflict ? <button type="button" onClick={() => void reload()}>Carregar dados atuais</button> : null}
    {state === "ready" && saved && fields ? <>
      <form className="side-card" onSubmit={submit} noValidate aria-labelledby="profile-data-title" aria-busy={busy}>
        <h2 id="profile-data-title">Nome e contatos</h2>
        {field("name", "Nome da loja", "Aparece no topo da vitrine. Até 120 caracteres. O endereço (slug) não muda.",
          <input id="profile-name" name="name" value={fields.name} onChange={event => update("name", event.target.value)} maxLength={240} autoComplete="organization" required disabled={disabled} aria-invalid={!!errors.name} aria-describedby={described("name")} />)}
        {field("contactEmail", "E-mail de contato", "Opcional. Mostrado na vitrine para o comprador falar com a loja.",
          <input id="profile-contactEmail" name="contactEmail" type="email" inputMode="email" value={fields.contactEmail} onChange={event => update("contactEmail", event.target.value)} maxLength={300} autoComplete="email" spellCheck={false} disabled={disabled} aria-invalid={!!errors.contactEmail} aria-describedby={described("contactEmail")} />)}
        {field("whatsapp", "WhatsApp", "Opcional. Número brasileiro com DDD, como (11) 98888-7777. A vitrine mostra um link para conversar.",
          <input id="profile-whatsapp" name="whatsapp" type="tel" inputMode="tel" value={fields.whatsapp} onChange={event => update("whatsapp", event.target.value)} maxLength={30} autoComplete="tel" disabled={disabled} aria-invalid={!!errors.whatsapp} aria-describedby={described("whatsapp")} />)}
        {field("instagram", "Instagram", "Opcional. Usuário, como @sualoja, ou o link do perfil.",
          <input id="profile-instagram" name="instagram" value={fields.instagram} onChange={event => update("instagram", event.target.value)} maxLength={80} autoComplete="off" spellCheck={false} disabled={disabled} aria-invalid={!!errors.instagram} aria-describedby={described("instagram")} />)}
        <div className="form-actions">
          <button type="submit" disabled={disabled}>{busy ? "Salvando…" : "Salvar dados da loja"}</button>
        </div>
      </form>

      <section className="side-card" aria-labelledby="profile-logo-title">
        <h2 id="profile-logo-title">Logo</h2>
        {saved.hasLogo
          // The version in the URL refreshes the preview after each change; the content is private.
          // eslint-disable-next-line @next/next/no-img-element
          ? <img className="store-logo-preview" src={`${path}/logo?v=${saved.version}`} alt={`Logo atual de ${saved.name}`} />
          : <p>Nenhum logo enviado. A vitrine mostra só o nome da loja.</p>}
        <form ref={logoForm} onSubmit={uploadLogo} noValidate aria-busy={busy}>
          <div className="form-field">
            <label htmlFor="profile-logo">{saved.hasLogo ? "Trocar logo" : "Enviar logo"}</label>
            <p id="profile-logo-help" className="field-help">JPEG ou PNG de até 5 MiB, com pelo menos 64 pixels em cada lado. Fundo transparente (PNG) funciona melhor.</p>
            <input id="profile-logo" name="file" type="file" accept="image/jpeg,image/png" disabled={disabled}
              onChange={event => { setLogo(event.target.files?.[0] ?? null); setLogoError(null); }}
              aria-invalid={!!logoError} aria-describedby={`profile-logo-help${logoError ? " profile-logo-error" : ""}`} />
            {logoError ? <p id="profile-logo-error" className="field-error">{logoError}</p> : null}
          </div>
          <div className="form-actions">
            <button type="submit" disabled={disabled}>{busy ? "Enviando…" : "Enviar logo"}</button>
            {saved.hasLogo ? <button type="button" className="button-secondary" disabled={disabled} onClick={() => void removeLogo()}>Remover logo</button> : null}
          </div>
        </form>
      </section>
    </> : null}
  </section>;
}
