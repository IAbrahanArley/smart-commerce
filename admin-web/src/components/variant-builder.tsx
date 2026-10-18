"use client";

import { useState } from "react";
import type { VariantDraft } from "@/lib/catalog";

export function variantLabel(options: { name: string; value: string }[]) {
  return options.map(option => `${option.name}: ${option.value}`).join(" · ");
}

export default function VariantBuilder({ variants, onChange, disabled }: { variants: VariantDraft[]; onChange: (value: VariantDraft[]) => void; disabled: boolean }) {
  const [options, setOptions] = useState([{ name: "", values: "" }]);
  const [error, setError] = useState("");
  function generate() {
    const definitions = options.map(option => ({ name: option.name.trim().normalize("NFC"), values: option.values.split(",").map(value => value.trim().normalize("NFC")) }));
    if (definitions.some(option => !option.name || [...option.name].length > 40 || /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u.test(option.name) || option.values.some(value => !value || [...value].length > 40 || /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u.test(value)) || new Set(option.values).size !== option.values.length)
      || new Set(definitions.map(option => option.name)).size !== definitions.length) { setError("Informe nomes e valores distintos, de 1 a 40 caracteres. Separe os valores por vírgula."); return; }
    const total = definitions.reduce((count, option) => count * option.values.length, 1);
    if (total > 50) { setError("Use até 50 combinações. Reduza a quantidade de valores antes de gerar."); return; }
    if (variants.length && !window.confirm("Gerar novamente substitui as combinações ainda não salvas. Deseja continuar?")) return;
    let combinations: { name: string; value: string }[][] = [[]];
    for (const option of definitions) combinations = combinations.flatMap(combination => option.values.map(value => [...combination, { name: option.name, value }]));
    onChange(combinations.map(combination => ({ sku: "", price: "", cost: "", options: combination }))); setError("");
  }
  return <div>
    <p>Até 3 tipos de opção e 50 combinações. Cada combinação terá SKU, preço e estoque próprios.</p>
    {options.map((option, index) => <fieldset className="variant-definition" key={index}><legend>Opção {index + 1}</legend>
      <label htmlFor={`option-name-${index}`}>Nome da opção</label><input id={`option-name-${index}`} value={option.name} placeholder={["Ex.: Cor", "Ex.: Tamanho", "Ex.: Material"][index]} maxLength={80} disabled={disabled} autoComplete="off" onChange={event => setOptions(current => current.map((item, position) => position === index ? { ...item, name: event.target.value } : item))} />
      <label htmlFor={`option-values-${index}`}>Valores separados por vírgula</label><input id={`option-values-${index}`} value={option.values} maxLength={4000} disabled={disabled} autoComplete="off" onChange={event => setOptions(current => current.map((item, position) => position === index ? { ...item, values: event.target.value } : item))} />
      {options.length > 1 ? <button type="button" disabled={disabled} onClick={() => setOptions(current => current.filter((_, position) => position !== index))}>Remover opção {index + 1}</button> : null}
    </fieldset>)}
    <div className="editor-tools">{options.length < 3 ? <button type="button" disabled={disabled} onClick={() => setOptions(current => [...current, { name: "", values: "" }])}>Adicionar tipo de opção</button> : null}<button type="button" disabled={disabled} onClick={generate}>Gerar combinações</button></div>
    {error ? <p role="alert" className="field-error">{error}</p> : null}
    {variants.map((variant, index) => <fieldset className="variant-definition" key={index}><legend>{variantLabel(variant.options)}</legend>
      {([['sku', 'SKU da combinação'], ['price', 'Preço (R$)'], ['cost', 'Custo (R$) — opcional']] as const).map(([field, label]) => <div className="form-field" key={field}><label htmlFor={`variant-${index}-${field}`}>{label}</label><input id={`variant-${index}-${field}`} name={`variant-${index}-${field}`} value={variant[field]} disabled={disabled} required={field !== "cost"} maxLength={field === "sku" ? 64 : 20} inputMode={field === "sku" ? "text" : "decimal"} autoComplete="off" spellCheck={false} onChange={event => onChange(variants.map((item, position) => position === index ? { ...item, [field]: event.target.value } : item))} /></div>)}
    </fieldset>)}
  </div>;
}
