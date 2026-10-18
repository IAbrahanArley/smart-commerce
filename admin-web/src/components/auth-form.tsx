"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { authRequest, requestError } from "@/lib/auth-api";

type Mode = "registration" | "login" | "activation";
const labels = {
  registration: { title: "Criar conta", action: "Enviar link de confirmação", pending: "Enviando link…" },
  login: { title: "Entrar", action: "Entrar na conta", pending: "Entrando…" },
  activation: { title: "Confirmar e-mail e definir senha", action: "Confirmar e-mail e criar senha", pending: "Confirmando…" },
};

export default function AuthForm({ mode }: { mode: Mode }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const submitting = useRef(false);
  const token = useRef("");
  const captured = useRef(false);
  const feedback = useRef<HTMLParagraphElement>(null);
  const password = useRef<HTMLInputElement>(null);
  const [passwordError, setPasswordError] = useState("");

  useEffect(() => {
    if (mode !== "activation" || captured.current) return;
    captured.current = true;
    token.current = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "";
    // Keep the action secret only in memory; GET never consumes it.
    window.history.replaceState(null, "", window.location.pathname);
  }, [mode]);

  useEffect(() => { if (error || success) feedback.current?.focus(); }, [error, success]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    setError("");
    setSuccess("");
    setPasswordError("");
    let normalizedPassword = String(values.get("password") ?? "");
    if (mode === "activation") {
      normalizedPassword = normalizedPassword.normalize("NFC");
      const length = [...normalizedPassword].length;
      if (length < 15 || length > 128 || !normalizedPassword.trim()) {
        setPasswordError("Use de 15 a 128 caracteres Unicode. Espaços são permitidos; evite uma senha composta somente por espaços.");
        password.current?.focus();
        return;
      }
      if (normalizedPassword !== String(values.get("confirmation") ?? "").normalize("NFC")) {
        setPasswordError("As senhas precisam ser iguais. Confira os dois campos.");
        password.current?.focus();
        return;
      }
      if (!token.current) {
        setError("Link ausente ou inválido. Solicite outro link de confirmação em Criar conta.");
        return;
      }
    }
    submitting.current = true;
    setPending(true);
    try {
      const body = mode === "activation"
        ? { token: token.current, password: normalizedPassword }
        : mode === "registration"
          ? { email: String(values.get("email")) }
          : { email: String(values.get("email")), password: normalizedPassword };
      const response = await authRequest(mode, body);
      if (!response.ok) {
        const message = mode === "login" && response.status === 401
          ? "Não foi possível entrar. Confira e-mail e senha e tente novamente."
          : mode === "activation" && response.status === 400
            ? "Não foi possível confirmar. O link pode estar vencido ou a senha não atende à política. Tente uma senha menos previsível ou solicite outro link."
            : requestError(response.status);
        setError(message);
        return;
      }
      form.reset();
      if (mode === "login") {
        router.replace("/conta");
        router.refresh();
      } else if (mode === "activation") {
        token.current = "";
        setSuccess("E-mail confirmado e senha definida. Entre na sua conta para continuar.");
      } else {
        setSuccess("Se a solicitação puder ser atendida, você receberá um link de confirmação. Em desenvolvimento, consulte a caixa local configurada. Aguarde pelo menos 60 segundos antes de reenviar.");
      }
    } catch {
      setError("Não foi possível conectar. Verifique a conexão e tente novamente.");
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }

  return (
    <section className="auth-panel" aria-labelledby="auth-title">
      <h1 id="auth-title">{labels[mode].title}</h1>
      <p>{mode === "registration" ? "Informe seu e-mail. Você definirá a senha somente depois de confirmar que o endereço pertence a você."
        : mode === "activation" ? "Escolha sua senha e confirme explicitamente a ativação. Abrir este link não ativa a conta."
          : "Use o e-mail confirmado e a senha da sua conta."}</p>
      <p ref={feedback} tabIndex={-1} role="status" aria-live="polite"
        className={error ? "feedback feedback-error" : success ? "feedback feedback-success" : "feedback-empty"}>
        {error || success}
      </p>
      {mode === "activation" && success ? null : (
        <form onSubmit={(event) => void submit(event)} aria-busy={pending}>
          {mode !== "activation" ? <div className="form-field">
            <label htmlFor="email">E-mail</label>
            <input id="email" name="email" type="email" autoComplete={mode === "login" ? "username" : "email"}
              inputMode="email" spellCheck={false} autoCapitalize="none" required maxLength={1024} />
          </div> : null}
          {mode !== "registration" ? <div className="form-field">
            <label htmlFor="password">Senha</label>
            {mode === "activation" ? <p id="password-help" className="field-help">Use de 15 a 128 caracteres Unicode. Espaços são permitidos. Acentos equivalentes são normalizados (NFC). Senhas comuns ou previsíveis são recusadas.</p> : null}
            <input ref={password} id="password" name="password" type="password" required
              autoComplete={mode === "login" ? "current-password" : "new-password"} maxLength={1024}
              aria-invalid={passwordError ? true : undefined}
              aria-describedby={mode === "activation" ? `password-help${passwordError ? " password-error" : ""}` : undefined} />
            {passwordError ? <p id="password-error" className="field-error" role="alert">{passwordError}</p> : null}
          </div> : null}
          {mode === "activation" ? <div className="form-field">
            <label htmlFor="confirmation">Confirme a senha</label>
            <input id="confirmation" name="confirmation" type="password" autoComplete="new-password" required maxLength={1024}
              aria-invalid={passwordError ? true : undefined} aria-describedby={passwordError ? "password-error" : undefined} />
          </div> : null}
          <button type="submit" disabled={pending}>{pending ? labels[mode].pending : labels[mode].action}</button>
        </form>
      )}
      <nav className="auth-links" aria-label="Opções da conta">
        {mode !== "login" ? <Link href="/entrar">Entrar</Link> : null}
        {mode !== "registration" ? <Link href="/cadastro">Criar conta ou solicitar outro link</Link> : null}
      </nav>
    </section>
  );
}
