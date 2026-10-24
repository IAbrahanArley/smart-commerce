"use client";

import { useRef, useState } from "react";
import { authRequest, requestError } from "@/lib/auth-api";
import { invalidateSession } from "@/lib/session-events";

// Single logout flow for the account page and the header menu; repeated clicks are ignored while pending.
export function useSignOut() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const submitting = useRef(false);

  async function signOut() {
    if (submitting.current) return;
    submitting.current = true;
    setPending(true);
    setError("");
    try {
      const response = await authRequest("logout");
      if (!response.ok && response.status !== 401) { setError(requestError(response.status)); return; }
      invalidateSession();
      // A document navigation discards account data and the client Router Cache.
      window.location.replace("/entrar");
    } catch { setError("Não foi possível sair. Verifique a conexão e tente novamente."); }
    finally { submitting.current = false; setPending(false); }
  }

  return { signOut, pending, error };
}
