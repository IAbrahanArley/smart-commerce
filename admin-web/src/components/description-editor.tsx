"use client";

import { useRef } from "react";

// SC-043: also edits the store's institutional pages (same sanitized format), with its own id and label.
export default function DescriptionEditor({ value, onChange, disabled, error, id = "product-descriptionHtml", label = "Descrição do produto — opcional", rows = 7 }: {
  value: string; onChange: (value: string) => void; disabled: boolean; error?: string; id?: string; label?: string; rows?: number;
}) {
  const help = `${id}-help`, errorId = `${id}-error`;
  const editor = useRef<HTMLTextAreaElement>(null);
  function insert(before: string, after: string) {
    const field = editor.current;
    if (!field) return;
    const start = field.selectionStart; const end = field.selectionEnd;
    const result = value.slice(0, start) + before + value.slice(start, end) + after + value.slice(end);
    if ([...result].length > 20000 || new TextEncoder().encode(result).length > 80000) return;
    onChange(result);
    requestAnimationFrame(() => { field.focus(); field.setSelectionRange(start + before.length, end + before.length); });
  }
  return <div className="form-field">
    <label htmlFor={id}>{label}</label>
    <div className="editor-tools" role="group" aria-label={`Formatação: ${label}`}>
      {[['Parágrafo', '<p>', '</p>'], ['Negrito', '<strong>', '</strong>'], ['Itálico', '<em>', '</em>'], ['Lista', '<ul><li>', '</li></ul>'], ['Quebra de linha', '<br>', '']].map(([label, before, after]) => <button key={label} type="button" disabled={disabled} onClick={() => insert(before, after)}>{label}</button>)}
    </div>
    <textarea ref={editor} id={id} name="descriptionHtml" rows={rows} maxLength={40000} value={value} onChange={event => onChange(event.target.value)} disabled={disabled} autoComplete="off" aria-invalid={!!error} aria-describedby={`${help}${error ? ` ${errorId}` : ""}`} />
    <p id={help} className="field-help">Use os botões ou escreva HTML básico. Parágrafos, listas, negrito e itálico são permitidos; scripts, links, estilos e HTML não permitido são removidos ao salvar. Até 20.000 caracteres.</p>
    {error ? <p id={errorId} className="field-error">{error}</p> : null}
  </div>;
}
