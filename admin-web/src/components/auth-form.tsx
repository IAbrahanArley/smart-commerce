"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Loader2 } from "lucide-react";
import { authRequest, requestError } from "@/lib/auth-api";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";

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
        setSuccess("Se a solicitação puder ser atendida, você receberá um link de confirmação. Aguarde pelo menos 60 segundos antes de reenviar.");
      }
    } catch {
      setError("Não foi possível conectar. Verifique a conexão e tente novamente.");
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }

  const description = mode === "registration" ? "Informe seu e-mail. Você definirá a senha somente depois de confirmar que o endereço pertence a você."
    : mode === "activation" ? "Escolha sua senha e confirme explicitamente a ativação. Abrir este link não ativa a conta."
      : mode === "forgot" ? "Informe o e-mail da sua conta. Enviaremos um link para você escolher uma nova senha."
        : mode === "reset" ? "Escolha uma nova senha. Abrir este link não altera nada até você salvar."
          : "Use o e-mail confirmado e a senha da sua conta.";
  const describedBy = (help: string) => [newPassword ? help : "", passwordError ? "password-error" : ""].filter(Boolean).join(" ") || undefined;

  return (
    <section className="w-full max-w-[26rem]" aria-labelledby="auth-title">
      <Link href="/" className="inline-flex items-center gap-3 rounded-md text-foreground no-underline hover:no-underline focus-visible:ring-2 focus-visible:ring-ring lg:hidden">
        <span className="inline-flex size-10 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground" aria-hidden="true">SC</span>
        <span className="text-lg font-bold" translate="no">Smart Commerce</span>
      </Link>
      <h1 id="auth-title" className="mb-0 mt-8 text-2xl font-bold lg:mt-0">{labels[mode].title}</h1>
      <p className="mb-0 mt-2 text-muted-foreground">{description}</p>
      <p ref={feedback} tabIndex={-1} role="status" aria-live="polite"
        className={error ? "mb-0 mt-6 rounded-md border border-destructive/30 bg-destructive-soft px-4 py-3 text-sm text-destructive"
          : success ? "mb-0 mt-6 rounded-md border border-success/30 bg-success-soft px-4 py-3 text-sm text-success" : "sr-only"}>
        {error || success}
      </p>
      {(newPassword || mode === "forgot") && success ? null : (
        <form onSubmit={(event) => void submit(event)} aria-busy={pending} className="mt-8 grid gap-5">
          {!newPassword ? <div className="grid gap-2">
            <Label htmlFor="email">E-mail</Label>
            <Input id="email" name="email" type="email" autoComplete={mode === "login" ? "username" : "email"}
              inputMode="email" spellCheck={false} autoCapitalize="none" required maxLength={1024} />
          </div> : null}
          {mode !== "registration" && mode !== "forgot" ? <div className="grid gap-2">
            <div className="flex items-center justify-between gap-3">
              <Label htmlFor="password">{mode === "reset" ? "Nova senha" : "Senha"}</Label>
              {mode === "login" ? <Link href="/esqueci-senha" className="text-sm font-medium">Esqueci minha senha</Link> : null}
            </div>
            {newPassword ? <p id="password-help" className="m-0 text-sm text-muted-foreground">Use de 15 a 128 caracteres Unicode. Espaços são permitidos. Acentos equivalentes são normalizados (NFC). Senhas comuns ou previsíveis são recusadas.</p> : null}
            <PasswordInput ref={password} id="password" name="password" required
              autoComplete={mode === "login" ? "current-password" : "new-password"} maxLength={1024}
              aria-invalid={passwordError ? true : undefined} aria-describedby={describedBy("password-help")} />
            {passwordError ? <p id="password-error" className="m-0 text-sm text-destructive" role="alert">{passwordError}</p> : null}
          </div> : null}
          {newPassword ? <div className="grid gap-2">
            <Label htmlFor="confirmation">{mode === "reset" ? "Confirme a nova senha" : "Confirme a senha"}</Label>
            <PasswordInput id="confirmation" name="confirmation" autoComplete="new-password" required maxLength={1024} toggleLabel="Mostrar confirmação da senha"
              aria-invalid={passwordError ? true : undefined} aria-describedby={passwordError ? "password-error" : undefined} />
          </div> : null}
          {mode === "registration" ? <p id="legal-notice" className="m-0 text-sm text-muted-foreground">Ao criar a conta, você concorda com os <Link href="/termos">Termos de uso</Link> e declara ter lido a <Link href="/privacidade">Política de privacidade</Link>.</p> : null}
          <Button type="submit" size="lg" className="w-full" disabled={pending} aria-describedby={mode === "registration" ? "legal-notice" : undefined}>
            {pending ? <Loader2 className="animate-spin motion-reduce:animate-none" aria-hidden="true" /> : null}
            {pending ? labels[mode].pending : labels[mode].action}
          </Button>
        </form>
      )}
      <nav className="mt-8 flex flex-wrap gap-x-6 gap-y-1 border-t border-border pt-6 text-sm" aria-label="Opções da conta">
        {mode === "login" ? <p className="m-0 inline-flex min-h-11 items-center gap-1.5 text-muted-foreground">Ainda não tem conta? <Link href="/cadastro" className="font-semibold">Criar conta</Link></p> : null}
        {mode === "registration" || mode === "forgot" ? <p className="m-0 inline-flex min-h-11 items-center gap-1.5 text-muted-foreground">Já tem conta? <Link href="/entrar" className="font-semibold">Entrar</Link></p> : null}
        {mode === "reset" ? <Link href="/esqueci-senha" className="inline-flex min-h-11 items-center font-semibold">Pedir um novo link</Link> : null}
        {mode === "activation" || mode === "reset" ? <Link href="/entrar" className="inline-flex min-h-11 items-center font-semibold">Entrar</Link> : null}
        {mode === "activation" ? <Link href="/cadastro" className="inline-flex min-h-11 items-center font-semibold">Solicitar outro link</Link> : null}
      </nav>
    </section>
  );
}
