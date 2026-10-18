"use client";

import { useRef } from "react";

export default function DescriptionEditor({ value, onChange, disabled, error }: {
  value: string; onChange: (value: string) => void; disabled: boolean; error?: string;
}) {
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
    <label htmlFor="product-descriptionHtml">Descrição do produto — opcional</label>
    <div className="editor-tools" role="group" aria-label="Formatação da descrição">
      {[['Parágrafo', '<p>', '</p>'], ['Negrito', '<strong>', '</strong>'], ['Itálico', '<em>', '</em>'], ['Lista', '<ul><li>', '</li></ul>'], ['Quebra de linha', '<br>', '']].map(([label, before, after]) => <button key={label} type="button" disabled={disabled} onClick={() => insert(before, after)}>{label}</button>)}
    </div>
    <textarea ref={editor} id="product-descriptionHtml" name="descriptionHtml" rows={7} maxLength={40000} value={value} onChange={event => onChange(event.target.value)} disabled={disabled} autoComplete="off" aria-invalid={!!error} aria-describedby={`product-description-help${error ? " product-description-error" : ""}`} />
    <p id="product-description-help" className="field-help">Use os botões ou escreva HTML básico. Parágrafos, listas, negrito e itálico são permitidos; scripts, links, estilos e HTML não permitido são removidos ao salvar. Até 20.000 caracteres.</p>
    {error ? <p id="product-description-error" className="field-error">{error}</p> : null}
  </div>;
}
