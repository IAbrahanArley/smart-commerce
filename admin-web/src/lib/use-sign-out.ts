"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { authRequest, requestError } from "@/lib/auth-api";

// Single logout flow for the account page and the header menu; repeated clicks are ignored while pending.
export function useSignOut() {
  const router = useRouter();
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
      if (!response.ok) { setError(requestError(response.status)); return; }
      router.replace("/entrar");
      router.refresh();
    } catch { setError("Não foi possível sair. Verifique a conexão e tente novamente."); }
    finally { submitting.current = false; setPending(false); }
  }

  return { signOut, pending, error };
}
