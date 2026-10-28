"use client";

import Image from "next/image";
import { type FormEvent, type ReactNode, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { type Category, categoryPath, parseCategories } from "@/lib/categories";
import {
  type Banner, type BannerDraft, type Section, EMPTY_BANNER, LINK_KINDS, MAX_BANNERS, MAX_SECTIONS, SECTION_KINDS,
  bannerBody, bannerDraft, bannerErrors, homeError, move, parseBanners, parseSections, sectionError,
} from "@/lib/home-content";
import { useConfirm } from "@/components/confirm-dialog";
import { Loading } from "@/components/ui/loading";

type Feedback = { text: string; error: boolean } | null;

// Sprint 8 (SC-043, ADR-0018 V8): the store home. Up to five banners (required description, link
// only inside the store, optional period, desktop and phone images) and the ordered sections.
export default function HomeContent({ tenantId }: { tenantId: string }) {
  const [banners, setBanners] = useState<Banner[] | null>(null);
  const [sections, setSections] = useState<Section[] | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [failed, setFailed] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<BannerDraft>(EMPTY_BANNER);
  const [errors, setErrors] = useState<Partial<Record<keyof BannerDraft, string>>>({});
  const [sectionErrors, setSectionErrors] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [version, setVersion] = useState(0);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const { confirm, dialog } = useConfirm();
  const tenantPath = `/api/tenants/${encodeURIComponent(tenantId)}`;
  const api = `${tenantPath}/content`;

  useEffect(() => {
    const controller = new AbortController();
    const signal = controller.signal;
    Promise.all([apiRequest(`${api}/banners`, { signal }), apiRequest(`${api}/sections`, { signal }), apiRequest(`${tenantPath}/categories`, { signal })])
      .then(async ([bannerResponse, sectionResponse, categoryResponse]) => {
        if (!bannerResponse.ok || !sectionResponse.ok) throw new Error();
        const parsedBanners = parseBanners(await bannerResponse.json()), parsedSections = parseSections(await sectionResponse.json());
        const parsedCategories = categoryResponse.ok ? parseCategories(await categoryResponse.json()) : null;
        if (!parsedBanners || !parsedSections) throw new Error();
        if (signal.aborted) return;
        setBanners(parsedBanners); setSections(parsedSections); setCategories(parsedCategories ?? []);
      }).catch(() => { if (!signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [api, tenantPath]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function call(path: string, init: { method: "POST" | "PUT" | "DELETE"; body?: object | FormData }, success: string, upload = false): Promise<boolean> {
    if (submitting.current) return false;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}${path}`, init);
      if (!response.ok) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        setFeedback({ text: upload && response.status === 400 ? "Envie uma imagem JPEG ou PNG com pelo menos 300 px no menor lado." : homeError(response.status, body.code), error: true });
        return false;
      }
      const value = await response.json();
      if (path.startsWith("/sections")) {
        const parsed = parseSections(value);
        if (!parsed) throw new Error();
        setSections(parsed);
      } else {
        const parsed = parseBanners(value);
        if (!parsed) throw new Error();
        setBanners(parsed); setVersion(current => current + 1);
      }
      setFeedback({ text: success, error: false });
      return true;
    } catch {
      setFeedback({ text: "Não foi possível confirmar. Recarregue a página antes de tentar de novo.", error: true });
      return false;
    } finally { submitting.current = false; setBusy(false); }
  }

  async function saveBanner(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = bannerErrors(draft);
    setErrors(found);
    const first = Object.keys(found)[0];
    if (first) { document.getElementById(`banner-${first}`)?.focus(); return; }
    const done = editing
      ? await call(`/banners/${encodeURIComponent(editing)}`, { method: "PUT", body: bannerBody(draft) }, "Banner salvo.")
      : await call("/banners", { method: "POST", body: bannerBody(draft) }, "Banner criado. Envie a imagem para ele aparecer na vitrine.");
    if (done) { setEditing(null); setDraft(EMPTY_BANNER); }
  }

  async function removeBanner(banner: Banner) {
    if (!(await confirm({ title: "Remover banner?", message: `O banner “${banner.alt}” sai da vitrine e as imagens dele são apagadas.`, confirmLabel: "Remover banner", tone: "danger" }))) return;
    if (await call(`/banners/${encodeURIComponent(banner.id)}`, { method: "DELETE" }, "Banner removido.") && editing === banner.id) { setEditing(null); setDraft(EMPTY_BANNER); }
  }

  function upload(banner: Banner, variant: "desktop" | "mobile", file: File | undefined) {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { setFeedback({ text: "Use uma imagem de até 5 MiB.", error: true }); return; }
    const body = new FormData(); body.set("file", file);
    void call(`/banners/${encodeURIComponent(banner.id)}/images/${variant}`, { method: "POST", body },
      variant === "desktop" ? "Imagem principal enviada." : "Imagem para celular enviada.", true);
  }

  function saveSections(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sections) return;
    const found: Record<number, string> = {};
    sections.forEach((section, index) => { const problem = sectionError(section); if (problem) found[index] = problem; });
    setSectionErrors(found);
    const first = Object.keys(found)[0];
    if (first !== undefined) { document.getElementById(`section-title-${first}`)?.focus(); return; }
    void call("/sections", { method: "PUT", body: { sections: sections.map(section => ({ ...section, title: section.title.trim() })) } }, "Seções da página inicial salvas.");
  }

  function updateSection(index: number, change: Partial<Section>) {
    setSections(current => current?.map((section, at) => at === index ? { ...section, ...change } : section) ?? null);
    setSectionErrors(current => ({ ...current, [index]: "" }));
  }

  if (failed) return <p role="alert" className="feedback feedback-error">Não foi possível consultar banners e seções. Recarregue a página.</p>;
  if (!banners || !sections) return <Loading context="banners e seções" />;
  const full = banners.length >= MAX_BANNERS;
  const categoryOptions = categories.map(category => ({ id: category.id, slug: category.slug, label: categoryPath(categories, category.id), published: category.published }));

  return <>
    {dialog}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    <section className="side-card content-card" aria-labelledby="banners-title">
      <h2 id="banners-title">Banners da página inicial</h2>
      <p className="field-help">Até {MAX_BANNERS} banners, na ordem abaixo. Imagem principal larga (por exemplo 1600 × 600 px); a de celular é opcional (por exemplo 800 × 800 px). JPEG ou PNG, até 5 MiB. Um banner sem imagem não aparece.</p>
      {banners.length === 0 ? <p className="field-help">Nenhum banner ainda.</p> : <ol className="banner-admin-list">
        {banners.map((banner, index) => <li key={banner.id} className="banner-admin-item">
          {banner.desktopImage
            ? <Image src={`${api}/banners/${encodeURIComponent(banner.id)}/images/desktop?v=${version}`} alt={banner.alt} width={320} height={120} unoptimized className="banner-admin-preview" />
            : <span className="banner-admin-preview banner-admin-empty">Sem imagem: não aparece na vitrine</span>}
          <div className="banner-admin-details">
            <strong>{banner.title ?? banner.alt}</strong>
            <span className="cell-hint">{LINK_KINDS.find(kind => kind.value === banner.linkKind)?.label}{banner.linkTarget ? `: ${banner.linkTarget}` : ""}
              {banner.startsAt || banner.endsAt ? ` · ${periodText(banner)}` : ""}{banner.mobileImage ? " · com imagem para celular" : ""}</span>
            <div className="banner-admin-uploads">
              <label className="button-secondary file-button">
                {banner.desktopImage ? "Trocar imagem principal" : "Enviar imagem principal"}
                <input type="file" accept="image/jpeg,image/png" className="visually-hidden" disabled={busy}
                  onChange={event => { upload(banner, "desktop", event.target.files?.[0]); event.target.value = ""; }} />
              </label>
              <label className="button-secondary file-button">
                {banner.mobileImage ? "Trocar imagem de celular" : "Enviar imagem de celular"}
                <input type="file" accept="image/jpeg,image/png" className="visually-hidden" disabled={busy}
                  onChange={event => { upload(banner, "mobile", event.target.files?.[0]); event.target.value = ""; }} />
              </label>
            </div>
            <div className="form-actions">
              <button type="button" className="button-secondary" disabled={busy || index === 0} aria-label={`Mover “${banner.alt}” para cima`}
                onClick={() => void call("/banners/order", { method: "PUT", body: { bannerIds: move(banners, index, -1).map(item => item.id) } }, "Ordem dos banners salva.")}>Subir</button>
              <button type="button" className="button-secondary" disabled={busy || index === banners.length - 1} aria-label={`Mover “${banner.alt}” para baixo`}
                onClick={() => void call("/banners/order", { method: "PUT", body: { bannerIds: move(banners, index, 1).map(item => item.id) } }, "Ordem dos banners salva.")}>Descer</button>
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Editar “${banner.alt}”`}
                onClick={() => { setEditing(banner.id); setDraft(bannerDraft(banner)); setErrors({}); document.getElementById("banner-alt")?.focus(); }}>Editar</button>
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Remover “${banner.alt}”`} onClick={() => void removeBanner(banner)}>Remover</button>
            </div>
          </div>
        </li>)}
      </ol>}
      <form onSubmit={event => void saveBanner(event)} noValidate className="banner-form" aria-labelledby="banner-form-title">
        <h3 id="banner-form-title">{editing ? "Editar banner" : "Novo banner"}</h3>
        <div className="content-fields">
          <Field id="banner-alt" label="Descrição da imagem" help="Obrigatória. Diga o que a imagem mostra, para leitores de tela." error={errors.alt}>
            <input id="banner-alt" value={draft.alt} maxLength={200} autoComplete="off" disabled={busy || (full && !editing)}
              onChange={event => { setDraft({ ...draft, alt: event.target.value }); setErrors({ ...errors, alt: undefined }); }}
              aria-invalid={!!errors.alt} aria-describedby={describedBy("banner-alt", errors.alt)} />
          </Field>
          <Field id="banner-title" label="Título (opcional)" help="Aparece abaixo da imagem." error={errors.title}>
            <input id="banner-title" value={draft.title} maxLength={80} autoComplete="off" disabled={busy || (full && !editing)}
              onChange={event => { setDraft({ ...draft, title: event.target.value }); setErrors({ ...errors, title: undefined }); }}
              aria-invalid={!!errors.title} aria-describedby={describedBy("banner-title", errors.title)} />
          </Field>
          <Field id="banner-linkKind" label="Ao clicar, leva para" help="Sempre uma página da própria loja.">
            <select id="banner-linkKind" value={draft.linkKind} disabled={busy || (full && !editing)} aria-describedby="banner-linkKind-help"
              onChange={event => { setDraft({ ...draft, linkKind: event.target.value as BannerDraft["linkKind"], linkTarget: "" }); setErrors({ ...errors, linkTarget: undefined }); }}>
              {LINK_KINDS.map(kind => <option key={kind.value} value={kind.value}>{kind.label}</option>)}
            </select>
          </Field>
          {draft.linkKind === "CATEGORY" ? <Field id="banner-linkTarget" label="Categoria" error={errors.linkTarget}>
            <select id="banner-linkTarget" value={draft.linkTarget} disabled={busy} aria-invalid={!!errors.linkTarget} aria-describedby={describedBy("banner-linkTarget", errors.linkTarget, false)}
              onChange={event => { setDraft({ ...draft, linkTarget: event.target.value }); setErrors({ ...errors, linkTarget: undefined }); }}>
              <option value="">Escolha</option>
              {categoryOptions.map(option => <option key={option.id} value={option.slug}>{option.label}{option.published ? "" : " (oculta)"}</option>)}
            </select>
          </Field> : null}
          {draft.linkKind === "PRODUCT" || draft.linkKind === "SEARCH" ? <Field id="banner-linkTarget"
            label={draft.linkKind === "PRODUCT" ? "Endereço do produto" : "Texto da busca"}
            help={draft.linkKind === "PRODUCT" ? "O endereço na vitrine, no cartão “Vitrine” do produto." : undefined} error={errors.linkTarget}>
            <input id="banner-linkTarget" value={draft.linkTarget} maxLength={100} autoComplete="off" spellCheck={draft.linkKind === "SEARCH"} disabled={busy}
              onChange={event => { setDraft({ ...draft, linkTarget: event.target.value }); setErrors({ ...errors, linkTarget: undefined }); }}
              aria-invalid={!!errors.linkTarget} aria-describedby={describedBy("banner-linkTarget", errors.linkTarget, draft.linkKind === "PRODUCT")} />
          </Field> : null}
          <Field id="banner-startsAt" label="Início (opcional)" error={errors.startsAt}>
            <input id="banner-startsAt" type="datetime-local" value={draft.startsAt} disabled={busy || (full && !editing)}
              onChange={event => { setDraft({ ...draft, startsAt: event.target.value }); setErrors({ ...errors, startsAt: undefined }); }}
              aria-invalid={!!errors.startsAt} aria-describedby={describedBy("banner-startsAt", errors.startsAt, false)} />
          </Field>
          <Field id="banner-endsAt" label="Fim (opcional)" error={errors.endsAt}>
            <input id="banner-endsAt" type="datetime-local" value={draft.endsAt} disabled={busy || (full && !editing)}
              onChange={event => { setDraft({ ...draft, endsAt: event.target.value }); setErrors({ ...errors, endsAt: undefined }); }}
              aria-invalid={!!errors.endsAt} aria-describedby={describedBy("banner-endsAt", errors.endsAt, false)} />
          </Field>
        </div>
        <div className="form-actions">
          <button type="submit" disabled={busy || (full && !editing)}>{busy ? "Salvando…" : editing ? "Salvar banner" : "Criar banner"}</button>
          {editing ? <button type="button" className="button-secondary" disabled={busy} onClick={() => { setEditing(null); setDraft(EMPTY_BANNER); setErrors({}); }}>Cancelar edição</button> : null}
        </div>
        {full && !editing ? <p className="field-help">A loja atingiu o limite de {MAX_BANNERS} banners.</p> : null}
      </form>
    </section>

    <form className="side-card content-card" onSubmit={saveSections} noValidate aria-labelledby="sections-title">
      <h2 id="sections-title">Seções da página inicial</h2>
      <p className="field-help">Blocos de até 8 produtos, na ordem abaixo. Sem nenhuma seção, a vitrine mostra os lançamentos. Uma seção sem produtos não aparece.</p>
      {sections.length === 0 ? <p className="field-help">Nenhuma seção: a vitrine mostra “Lançamentos”.</p> : <ol className="section-admin-list">
        {sections.map((section, index) => <li key={index} className="section-admin-item">
          <div className="content-fields">
            <div className="form-field">
              <label htmlFor={`section-kind-${index}`}>Tipo</label>
              <select id={`section-kind-${index}`} value={section.kind} disabled={busy}
                onChange={event => { const kind = event.target.value as Section["kind"]; updateSection(index, { kind, categoryId: kind === "CATEGORY" ? section.categoryId : null, title: section.title || (SECTION_KINDS.find(item => item.value === kind)?.title ?? "") }); }}>
                {SECTION_KINDS.map(kind => <option key={kind.value} value={kind.value}>{kind.label}</option>)}
              </select>
            </div>
            {section.kind === "CATEGORY" ? <div className="form-field">
              <label htmlFor={`section-category-${index}`}>Categoria</label>
              <select id={`section-category-${index}`} value={section.categoryId ?? ""} disabled={busy}
                onChange={event => { const option = categoryOptions.find(item => item.id === event.target.value); updateSection(index, { categoryId: event.target.value || null, title: section.title || (option ? categories.find(item => item.id === option.id)?.name ?? "" : "") }); }}>
                <option value="">Escolha</option>
                {categoryOptions.map(option => <option key={option.id} value={option.id}>{option.label}{option.published ? "" : " (oculta)"}</option>)}
              </select>
            </div> : null}
            <div className="form-field">
              <label htmlFor={`section-title-${index}`}>Título na vitrine</label>
              <input id={`section-title-${index}`} value={section.title} maxLength={60} autoComplete="off" disabled={busy}
                onChange={event => updateSection(index, { title: event.target.value })}
                aria-invalid={!!sectionErrors[index]} aria-describedby={sectionErrors[index] ? `section-error-${index}` : undefined} />
            </div>
          </div>
          {sectionErrors[index] ? <p id={`section-error-${index}`} className="field-error">{sectionErrors[index]}</p> : null}
          <div className="form-actions">
            <button type="button" className="button-secondary" disabled={busy || index === 0} aria-label={`Mover seção ${index + 1} para cima`} onClick={() => setSections(move(sections, index, -1))}>Subir</button>
            <button type="button" className="button-secondary" disabled={busy || index === sections.length - 1} aria-label={`Mover seção ${index + 1} para baixo`} onClick={() => setSections(move(sections, index, 1))}>Descer</button>
            <button type="button" className="button-secondary" disabled={busy} aria-label={`Remover seção ${index + 1}`} onClick={() => { setSections(sections.filter((_, at) => at !== index)); setSectionErrors({}); }}>Remover</button>
          </div>
        </li>)}
      </ol>}
      <div className="form-actions">
        <button type="button" className="button-secondary" disabled={busy || sections.length >= MAX_SECTIONS}
          onClick={() => setSections([...sections, { kind: "NEW", title: "Lançamentos", categoryId: null }])}>Adicionar seção</button>
        <button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar seções"}</button>
      </div>
    </form>
  </>;
}

function Field({ id, label, help, error, children }: { id: string; label: string; help?: string; error?: string; children: ReactNode }) {
  return <div className="form-field">
    <label htmlFor={id}>{label}</label>
    {help ? <p id={`${id}-help`} className="field-help">{help}</p> : null}
    {children}
    {error ? <p id={`${id}-error`} className="field-error">{error}</p> : null}
  </div>;
}

function describedBy(id: string, error: string | undefined, help = true): string | undefined {
  return [help ? `${id}-help` : "", error ? `${id}-error` : ""].filter(Boolean).join(" ") || undefined;
}

function periodText(banner: Banner): string {
  const format = (iso: string) => new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
  if (banner.startsAt && banner.endsAt) return `de ${format(banner.startsAt)} a ${format(banner.endsAt)}`;
  return banner.startsAt ? `a partir de ${format(banner.startsAt)}` : `até ${format(banner.endsAt!)}`;
}
