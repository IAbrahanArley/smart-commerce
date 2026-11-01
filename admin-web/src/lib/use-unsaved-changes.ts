import { useEffect } from "react";

export const UNSAVED_CHANGES = "Há alterações não salvas. Deseja sair sem salvar?";

/** Warns before leaving the page (reload, close or an in-app link) while a form has unsaved changes. */
export function useUnsavedChanges(dirty: boolean, message = UNSAVED_CHANGES) {
  useEffect(() => {
    if (!dirty) return;
    const leave = (event: BeforeUnloadEvent) => event.preventDefault();
    const navigate = (event: MouseEvent) => {
      const link = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      // Same-page anchors (the skip link) and new tabs or downloads do not leave the form.
      if (link.getAttribute("href")?.startsWith("#") || link.getAttribute("target") === "_blank" || link.hasAttribute("download")) return;
      if (!window.confirm(message)) { event.preventDefault(); event.stopPropagation(); }
    };
    window.addEventListener("beforeunload", leave);
    document.addEventListener("click", navigate, true);
    return () => { window.removeEventListener("beforeunload", leave); document.removeEventListener("click", navigate, true); };
  }, [dirty, message]);
}
