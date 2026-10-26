"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { authRequest, requestError } from "@/lib/auth-api";

// "forgot" and "reset": password recovery (MVP block 1a, RELEASE_PLAN.md).
type Mode = "registration" | "login" | "activation" | "forgot" | "reset";
const labels = {
  registration: { title: "Criar conta", action: "Enviar link de confirmação", pending: "Enviando link…" },
  login: { title: "Entrar", action: "Entrar na conta", pending: "Entrando…" },
  activation: { title: "Confirmar e-mail e definir senha", action: "Confirmar e-mail e criar senha", pending: "Confirmando…" },
  forgot: { title: "Esqueci minha senha", action: "Enviar link de redefinição", pending: "Enviando link…" },
  reset: { title: "Redefinir senha", action: "Salvar nova senha", pending: "Salvando…" },
};
const paths: Record<Mode, string> = {
  registration: "registration", login: "login", activation: "activation",
  forgot: "password-reset/request", reset: "password-reset/confirmation",
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

  const newPassword = mode === "activation" || mode === "reset";

  useEffect(() => {
    if (!newPassword || captured.current) return;
    captured.current = true;
    token.current = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "";
    // Keep the action secret only in memory; GET never consumes it.
    window.history.replaceState(null, "", window.location.pathname);
  }, [newPassword]);

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
    if (newPassword) {
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
        setError(mode === "reset"
          ? "Link ausente ou inválido. Peça um novo link em Esqueci minha senha."
          : "Link ausente ou inválido. Solicite outro link de confirmação em Criar conta.");
        return;
      }
    }
    submitting.current = true;
    setPending(true);
    try {
      const body = newPassword
        ? { token: token.current, password: normalizedPassword }
        : mode === "registration" || mode === "forgot"
          ? { email: String(values.get("email")) }
          : { email: String(values.get("email")), password: normalizedPassword };
      const response = await authRequest(paths[mode], body);
      if (!response.ok) {
        const message = mode === "login" && response.status === 401
          ? "Não foi possível entrar. Confira e-mail e senha e tente novamente."
          : mode === "activation" && response.status === 400
            ? "Não foi possível confirmar. O link pode estar vencido ou a senha não atende à política. Tente uma senha menos previsível ou solicite outro link."
            : mode === "reset" && response.status === 400
              ? "Não foi possível redefinir. O link pode estar vencido ou já ter sido usado, ou a senha não atende à política. Tente uma senha menos previsível ou peça um novo link."
              : requestError(response.status);
        setError(message);
        return;
      }
      form.reset();
      if (mode === "login") {
        router.replace("/lojas");
        router.refresh();
      } else if (mode === "activation") {
        token.current = "";
        setSuccess("E-mail confirmado e senha definida. Entre na sua conta para continuar.");
      } else if (mode === "reset") {
        token.current = "";
        setSuccess("Senha redefinida. Por segurança, todas as sessões abertas foram encerradas. Entre com a nova senha.");
      } else if (mode === "forgot") {
        setSuccess("Se houver uma conta ativa com este e-mail, você receberá um link para redefinir a senha. Ele vale por 30 minutos. Aguarde pelo menos 60 segundos antes de pedir outro.");
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
          : mode === "forgot" ? "Informe o e-mail da sua conta. Enviaremos um link para você escolher uma nova senha."
            : mode === "reset" ? "Escolha uma nova senha. Abrir este link não altera nada até você salvar."
              : "Use o e-mail confirmado e a senha da sua conta."}</p>
      <p ref={feedback} tabIndex={-1} role="status" aria-live="polite"
        className={error ? "feedback feedback-error" : success ? "feedback feedback-success" : "feedback-empty"}>
        {error || success}
      </p>
      {(newPassword || mode === "forgot") && success ? null : (
        <form onSubmit={(event) => void submit(event)} aria-busy={pending}>
          {!newPassword ? <div className="form-field">
            <label htmlFor="email">E-mail</label>
            <input id="email" name="email" type="email" autoComplete={mode === "login" ? "username" : "email"}
              inputMode="email" spellCheck={false} autoCapitalize="none" required maxLength={1024} />
          </div> : null}
          {mode !== "registration" && mode !== "forgot" ? <div className="form-field">
            <label htmlFor="password">{mode === "reset" ? "Nova senha" : "Senha"}</label>
            {newPassword ? <p id="password-help" className="field-help">Use de 15 a 128 caracteres Unicode. Espaços são permitidos. Acentos equivalentes são normalizados (NFC). Senhas comuns ou previsíveis são recusadas.</p> : null}
            <input ref={password} id="password" name="password" type="password" required
              autoComplete={mode === "login" ? "current-password" : "new-password"} maxLength={1024}
              aria-invalid={passwordError ? true : undefined}
              aria-describedby={newPassword ? `password-help${passwordError ? " password-error" : ""}` : undefined} />
            {passwordError ? <p id="password-error" className="field-error" role="alert">{passwordError}</p> : null}
          </div> : null}
          {newPassword ? <div className="form-field">
            <label htmlFor="confirmation">{mode === "reset" ? "Confirme a nova senha" : "Confirme a senha"}</label>
            <input id="confirmation" name="confirmation" type="password" autoComplete="new-password" required maxLength={1024}
              aria-invalid={passwordError ? true : undefined} aria-describedby={passwordError ? "password-error" : undefined} />
          </div> : null}
          {mode === "registration" ? <p id="legal-notice" className="legal-notice">Ao criar a conta, você concorda com os <Link href="/termos">Termos de uso</Link> e declara ter lido a <Link href="/privacidade">Política de privacidade</Link>.</p> : null}
          <button type="submit" disabled={pending} aria-describedby={mode === "registration" ? "legal-notice" : undefined}>{pending ? labels[mode].pending : labels[mode].action}</button>
        </form>
      )}
      <nav className="auth-links" aria-label="Opções da conta">
        {mode !== "login" ? <Link href="/entrar">Entrar</Link> : null}
        {mode === "login" ? <Link href="/esqueci-senha">Esqueci minha senha</Link> : null}
        {mode === "reset" ? <Link href="/esqueci-senha">Pedir um novo link</Link> : null}
        {mode !== "registration" && mode !== "forgot" && mode !== "reset" ? <Link href="/cadastro">Criar conta ou solicitar outro link</Link> : null}
      </nav>
    </section>
  );
}
