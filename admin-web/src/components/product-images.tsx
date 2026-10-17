"use client";

import Image from "next/image";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { apiRequest } from "@/lib/auth-api";

type Photo = { id: string; alt: string; width: number; height: number };
type Images = { images: Photo[]; version: number };

export default function ProductImages({ tenantId, productId, version, locked, onVersion, onReload }: {
  tenantId: string; productId: string; version: number; locked: boolean; onVersion: (version: number) => void; onReload: () => void;
}) {
  const path = `/api/tenants/${encodeURIComponent(tenantId)}/products/${encodeURIComponent(productId)}/images`;
  const [data, setData] = useState<Images | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [alt, setAlt] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const form = useRef<HTMLFormElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const submitting = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest(path, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error();
      const result: Images = await response.json();
      if (!controller.signal.aborted) setData(result);
    }).catch(() => { if (!controller.signal.aborted) setFeedback({ text: "Não foi possível consultar as fotos. Tente novamente.", error: true }); });
    return () => controller.abort();
  }, [path, attempt, version]);
  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  const stale = data !== null && data.version !== version;
  const disabled = busy || locked || stale || uncertain || !data;

  async function mutate(operation: () => Promise<Response>) {
    if (submitting.current || disabled) return;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await operation();
      if (response.status === 409) { setUncertain(true); setFeedback({ text: "O produto foi alterado. Carregue a versão atual antes de modificar as fotos.", error: true }); return; }
      if (!response.ok) { setFeedback({ text: response.status === 413 ? "A foto excede o tamanho permitido. Use JPEG ou PNG de até 5 MiB." : "Não foi possível alterar as fotos. Confira o formato, tamanho e texto alternativo e tente novamente.", error: true }); return; }
      const result: Images = await response.json();
      setData(result); onVersion(result.version); setAlt(""); setFile(null); form.current?.reset();
      setFeedback({ text: "Fotos atualizadas. A primeira foto é a capa do produto.", error: false });
    } catch { setUncertain(true); setFeedback({ text: "Não foi possível confirmar a alteração. Carregue a versão atual antes de repetir para evitar duplicação.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }
  function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || !["image/jpeg", "image/png"].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0 || !alt.trim() || [...alt.trim()].length > 200 || /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u.test(alt)) {
      setFeedback({ text: "Selecione JPEG ou PNG de até 5 MiB e informe um texto alternativo de 1 a 200 caracteres, sem quebras de linha.", error: true }); return;
    }
    const body = new FormData(); body.set("file", file); body.set("alt", alt.trim()); body.set("version", String(version));
    void mutate(() => apiRequest(path, { method: "POST", body }));
  }
  function move(index: number, target: number) {
    if (!data) return;
    const ids = data.images.map(image => image.id);
    const [selected] = ids.splice(index, 1); ids.splice(target, 0, selected);
    void mutate(() => apiRequest(`${path}/order`, { method: "PUT", body: { imageIds: ids, version } }));
  }
  return <section className="product-section" aria-labelledby="photos-title"><h2 id="photos-title">Fotos do produto</h2>
    <p>Até 6 fotos JPEG ou PNG, de até 5 MiB cada e entre 300 e 4.096 pixels por lado. A primeira é a capa. Descreva o conteúdo para quem não vê a imagem.</p>
    {locked ? <p className="notice">Salve as alterações do produto antes de modificar as fotos.</p> : null}
    {stale || uncertain ? <><p role="alert" className="feedback feedback-error">Atualize o produto para consultar a versão atual das fotos.</p><button type="button" disabled={busy || locked} onClick={onReload}>Carregar versão atual do produto</button></> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {!data ? <>{!feedback ? <p role="status">Consultando fotos…</p> : null}<button type="button" onClick={() => { setFeedback(null); setAttempt(current => current + 1); }}>Consultar fotos novamente</button></> : !data.images.length ? <p>Nenhuma foto cadastrada.</p> : <ol className="photo-list">{data.images.map((photo, index) => <li key={photo.id} className="photo-item">
      <Image src={`${path}/${encodeURIComponent(photo.id)}/content`} alt={photo.alt} width={photo.width} height={photo.height} unoptimized className="product-photo" />
      <p>{index === 0 ? "Capa" : `Foto ${index + 1}`} — {photo.alt}</p>
      <div className="editor-tools">{index > 0 ? <><button type="button" disabled={disabled} onClick={() => move(index, 0)} aria-label={`Usar foto ${index + 1} como capa`}>Usar como capa</button><button type="button" disabled={disabled} onClick={() => move(index, index - 1)} aria-label={`Mover foto ${index + 1} para antes`}>Mover para antes</button></> : null}
        {index < data.images.length - 1 ? <button type="button" disabled={disabled} onClick={() => move(index, index + 1)} aria-label={`Mover foto ${index + 1} para depois`}>Mover para depois</button> : null}
        <button type="button" disabled={disabled} onClick={() => { if (window.confirm(`Remover a foto ${index + 1}?`)) void mutate(() => apiRequest(`${path}/${encodeURIComponent(photo.id)}?version=${version}`, { method: "DELETE" })); }} aria-label={`Remover foto ${index + 1}`}>Remover foto</button>
      </div>
    </li>)}</ol>}
    {data && data.images.length < 6 ? <form ref={form} onSubmit={upload} aria-busy={busy}>
      <div className="form-field"><label htmlFor="product-photo-file">Nova foto</label><input id="product-photo-file" name="file" type="file" accept="image/jpeg,image/png" required disabled={disabled} onChange={event => setFile(event.target.files?.[0] ?? null)} aria-describedby="photo-upload-help" /><p id="photo-upload-help" className="field-help">JPEG ou PNG de até 5 MiB. O arquivo é validado pelo servidor.</p></div>
      <div className="form-field"><label htmlFor="product-photo-alt">Texto alternativo</label><input id="product-photo-alt" name="alt" value={alt} onChange={event => setAlt(event.target.value)} maxLength={400} required disabled={disabled} autoComplete="off" /></div>
      <button type="submit" disabled={disabled}>{busy ? "Atualizando fotos…" : "Enviar foto"}</button>
    </form> : null}
  </section>;
}
