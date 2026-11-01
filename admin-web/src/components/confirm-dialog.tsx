"use client";

import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from "react";

export type ConfirmOptions = { title: string; message: ReactNode; confirmLabel: string; cancelLabel?: string; tone?: "default" | "danger" };

// Confirmation in a native modal <dialog> (DESIGN.md: Diálogo de confirmação). The browser keeps focus
// inside, Escape cancels, and focus returns to the control that opened it. Cancel comes first in the
// focus order, so Enter on open never confirms by accident. The backend still validates every action.
export function useConfirm() {
  const dialog = useRef<HTMLDialogElement>(null);
  const resolver = useRef<((confirmed: boolean) => void) | null>(null);
  const opener = useRef<HTMLElement | null>(null);
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const id = useId();

  const confirm = useCallback((next: ConfirmOptions) => new Promise<boolean>(resolve => {
    resolver.current?.(false);
    resolver.current = resolve;
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setOptions(next);
  }), []);

  useEffect(() => {
    const element = dialog.current;
    if (!options || !element || element.open) return;
    element.returnValue = "";
    element.showModal();
  }, [options]);

  useEffect(() => () => resolver.current?.(false), []);

  function closed() {
    const resolve = resolver.current;
    resolver.current = null;
    resolve?.(dialog.current?.returnValue === "confirm");
    setOptions(null);
    opener.current?.focus();
  }

  const element = <dialog ref={dialog} className="confirm-dialog" aria-labelledby={options ? `${id}-title` : undefined} aria-describedby={options ? `${id}-message` : undefined} onClose={closed}>
    {options ? <>
      <h2 id={`${id}-title`}>{options.title}</h2>
      <div id={`${id}-message`} className="confirm-dialog-message">{options.message}</div>
      <div className="form-actions confirm-dialog-actions">
        <button type="button" className="button-secondary" onClick={() => dialog.current?.close("cancel")}>{options.cancelLabel ?? "Cancelar"}</button>
        <button type="button" className={options.tone === "danger" ? "button-danger" : undefined} onClick={() => dialog.current?.close("confirm")}>{options.confirmLabel}</button>
      </div>
    </> : null}
  </dialog>;

  return { confirm, dialog: element };
}
